#!/usr/bin/python3

# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

import argparse
import logging
import os
import random
import socket
import string
import subprocess
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import List, Optional, Tuple

LOG_LEVELS = {
    "critical": logging.CRITICAL,
    "error": logging.ERROR,
    "warn": logging.WARNING,
    "warning": logging.WARNING,
    "info": logging.INFO,
    "debug": logging.DEBUG,
}

GLIDE_HOME_DIR = os.getenv("GLIDE_HOME_DIR") or f"{__file__}/../.."
CLUSTERS_FOLDER = os.getenv("CLUSTERS_FOLDER") or os.path.abspath(
    f"{GLIDE_HOME_DIR}/utils/clusters"
)
TLS_FOLDER = os.path.abspath(f"{GLIDE_HOME_DIR}/utils/tls_crts")
CA_CRT = f"{TLS_FOLDER}/ca.crt"
REDIS_CRT = f"{TLS_FOLDER}/redis.crt"
REDIS_KEY = f"{TLS_FOLDER}/redis.key"


def init_logger(logfile: str):
    print(f"LOG_FILE={logfile}")
    root_logger = logging.getLogger()
    handler = logging.FileHandler(logfile, "w", "utf-8")
    root_logger.addHandler(handler)


def check_if_tls_cert_exist(tls_file: str, timeout: int = 15):
    timeout_start = time.time()
    while time.time() < timeout_start + timeout:
        if os.path.exists(tls_file):
            return True
        else:
            time.sleep(0.005)
    logging.warn(f"Timed out waiting for certificate file {tls_file}")
    return False


def check_if_tls_cert_is_valid(tls_file: str):
    file_creation_unix_time = os.path.getmtime(tls_file)
    file_creation_utc = datetime.fromtimestamp(file_creation_unix_time)
    current_time_utc = datetime.utcnow()
    time_since_created = current_time_utc - file_creation_utc
    return time_since_created.days < 3650


def should_generate_new_tls_certs() -> bool:
    # Returns False if we already have existing and valid TLS files, otherwise True
    try:
        Path(TLS_FOLDER).mkdir(exist_ok=False)
    except FileExistsError:
        files_list = [CA_CRT, REDIS_KEY, REDIS_CRT]
        for file in files_list:
            if check_if_tls_cert_exist(file) and check_if_tls_cert_is_valid(file):
                return False
    return True


def generate_tls_certs():
    # Based on shell script in redis's server tests
    # https://github.com/redis/redis/blob/8c291b97b95f2e011977b522acf77ead23e26f55/utils/gen-test-certs.sh
    logging.debug("## Generating TLS certificates")
    tic = time.perf_counter()
    ca_key = f"{TLS_FOLDER}/ca.key"
    ca_serial = f"{TLS_FOLDER}/ca.txt"
    ext_file = f"{TLS_FOLDER}/openssl.cnf"

    f = open(ext_file, "w")
    f.write("keyUsage = digitalSignature, keyEncipherment")
    f.close()

    def make_key(name: str, size: int):
        p = subprocess.Popen(
            [
                "openssl",
                "genrsa",
                "-out",
                name,
                str(size),
            ],
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
        )
        output, err = p.communicate(timeout=10)
        if p.returncode != 0:
            raise Exception(
                f"Failed to make key for {name}. Executed: {str(p.args)}:\n{err}"
            )

    # Build CA key
    make_key(ca_key, 4096)

    # Build redis key
    make_key(REDIS_KEY, 2048)

    # Build CA Cert
    p = subprocess.Popen(
        [
            "openssl",
            "req",
            "-x509",
            "-new",
            "-nodes",
            "-sha256",
            "-key",
            ca_key,
            "-days",
            "3650",
            "-subj",
            "/O=Redis Test/CN=Certificate Authority",
            "-out",
            CA_CRT,
        ],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
    )
    output, err = p.communicate(timeout=10)
    if p.returncode != 0:
        raise Exception(
            f"Failed to make create CA cert. Executed: {str(p.args)}:\n{err}"
        )

    # Read Redis key
    p1 = subprocess.Popen(
        [
            "openssl",
            "req",
            "-new",
            "-sha256",
            "-subj",
            "/O=Redis Test/CN=Generic-cert",
            "-key",
            REDIS_KEY,
        ],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
    )
    _redis_key_output, err = p.communicate(timeout=10)
    if p.returncode != 0:
        raise Exception(f"Failed to read Redis key. Executed: {str(p.args)}:\n{err}")

    # Build redis cert
    p = subprocess.Popen(
        [
            "openssl",
            "x509",
            "-req",
            "-sha256",
            "-CA",
            CA_CRT,
            "-CAkey",
            ca_key,
            "-CAserial",
            ca_serial,
            "-CAcreateserial",
            "-days",
            "3650",
            "-extfile",
            ext_file,
            "-out",
            REDIS_CRT,
        ],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        stdin=p1.stdout,
        text=True,
    )
    output, err = p.communicate(timeout=10)
    if p.returncode != 0:
        raise Exception(f"Failed to create redis cert. Executed: {str(p.args)}:\n{err}")
    toc = time.perf_counter()
    logging.debug(f"generate_tls_certs() Elapsed time: {toc - tic:0.4f}")
    logging.debug(f"TLS files= {REDIS_CRT}, {REDIS_KEY}, {CA_CRT}")


def get_redis_cli_option_args(
    cluster_folder: str, use_tls: bool, auth: Optional[str] = None
) -> List[str]:
    args = (
        [
            "--tls",
            "--cert",
            REDIS_CRT,
            "--key",
            REDIS_KEY,
            "--cacert",
            CA_CRT,
        ]
        if use_tls
        else []
    )
    if auth:
        args.extend(["-a", auth])
    return args


def get_random_string(length):
    letters = string.ascii_letters + string.digits
    result_str = "".join(random.choice(letters) for i in range(length))
    return result_str


class RedisServer:
    def __init__(self, host: str, port: int) -> None:
        self.host = host
        self.port = port

    def __str__(self) -> str:
        return f"{self.host}:{self.port}"


def next_free_port(
    min_port: int = 6379, max_port: int = 55535, timeout: int = 60
) -> int:
    tic = time.perf_counter()
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    timeout_start = time.time()
    while time.time() < timeout_start + timeout:
        try:
            port = random.randint(min_port, max_port)
            logging.debug(f"Trying port {port}")
            sock.bind(("127.0.0.1", port))
            sock.close()
            toc = time.perf_counter()
            logging.debug(f"next_free_port() is {port} Elapsed time: {toc - tic:0.4f}")
            return port
        except OSError as e:
            logging.warning(f"next_free_port error for port {port}: {e}")
            # Sleep so we won't spam the system with sockets
            time.sleep(random.randint(0, 9) / 10000 + 0.01)
            continue
    logging.error("Timeout Expired: No free port found")
    raise Exception("Timeout Expired: No free port found")


def create_cluster_folder(path: str, prefix: str) -> str:
    """Create the cluster's main folder

    Args:
        path (str): the path to create the folder in
        prefix (str): a prefix for the cluster folder name

    Returns:
        str: The full path of the cluster folder
    """
    time = datetime.now(timezone.utc)
    time_str = time.strftime("%Y-%m-%dT%H-%M-%SZ")
    cluster_folder = f"{path}/{prefix}-{time_str}-{get_random_string(6)}"
    logging.debug(f"## Creating cluster folder in {cluster_folder}")
    Path(cluster_folder).mkdir(exist_ok=True)
    return cluster_folder


def start_redis_server(
    host: str,
    port: Optional[int],
    cluster_folder: str,
    tls: bool,
    tls_args: List[str],
    cluster_mode: bool,
    load_module: Optional[List[str]] = None,
) -> Tuple[RedisServer, str]:
    port = port if port else next_free_port()
    logging.debug(f"Creating server {host}:{port}")
    # Create sub-folder for each node
    node_folder = f"{cluster_folder}/{port}"
    Path(node_folder).mkdir(exist_ok=True)
    cmd_args = [
        "redis-server",
        f"{'--tls-port' if tls else '--port'}",
        str(port),
        "--cluster-enabled",
        f"{'yes' if cluster_mode else 'no'}",
        "--dir",
        node_folder,
        "--daemonize",
        "yes",
        "--logfile",
        f"{node_folder}/redis.log",
    ]
    if load_module:
        if len(load_module) == 0:
            raise ValueError(
                "Please provide the path(s) to the module(s) you want to load."
            )
        for module_path in load_module:
            cmd_args.extend(["--loadmodule", module_path])
    cmd_args += tls_args
    p = subprocess.Popen(
        cmd_args,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
    )
    output, err = p.communicate(timeout=2)
    if p.returncode != 0:
        raise Exception(
            f"Failed to execute command: {str(p.args)}\n Return code: {p.returncode}\n Error: {err}"
        )
    server = RedisServer(host, port)
    return server, node_folder


def create_servers(
    host: str,
    shard_count: int,
    replica_count: int,
    ports: Optional[List[int]],
    cluster_folder: str,
    tls: bool,
    cluster_mode: bool,
    load_module: Optional[List[str]] = None,
) -> List[RedisServer]:
    tic = time.perf_counter()
    logging.debug("## Creating servers")
    ready_servers: List[RedisServer] = []
    nodes_count = shard_count * (1 + replica_count)
    tls_args = []
    if tls is True:
        if should_generate_new_tls_certs():
            generate_tls_certs()
        tls_args = [
            "--tls-cluster",
            "yes",
            "--tls-cert-file",
            REDIS_CRT,
            "--tls-key-file",
            REDIS_KEY,
            "--tls-ca-cert-file",
            CA_CRT,
            "--tls-auth-clients",  # Make it so client doesn't have to send cert
            "no",
            "--bind",
            host,
            "--port",
            "0",
        ]
        if replica_count > 0:
            tls_args.append("--tls-replication")
            tls_args.append("yes")
    servers_to_check = set()
    # Start all servers
    for i in range(nodes_count):
        port = ports[i] if ports else None
        servers_to_check.add(
            start_redis_server(
                host, port, cluster_folder, tls, tls_args, cluster_mode, load_module
            )
        )
    # Check all servers
    while len(servers_to_check) > 0:
        server, node_folder = servers_to_check.pop()
        logging.debug(f"Checking server {server.host}:{server.port}")
        if is_address_already_in_use(server, f"{node_folder}/redis.log"):
            remove_folder(node_folder)
            if ports is not None:
                # The user passed a taken port, exit with an error
                raise Exception(
                    f"Couldn't start redis on {server.host}:{server.port}, address already in use"
                )
            # The port was already taken, try to find a new free one
            servers_to_check.add(
                start_redis_server(
                    server.host,
                    None,
                    cluster_folder,
                    tls,
                    tls_args,
                    cluster_mode,
                    load_module,
                )
            )
            continue
        if not wait_for_server(server, cluster_folder, tls):
            raise Exception(
                f"Waiting for server {server.host}:{server.port} to start exceeded timeout.\n"
                f"See {node_folder}/redis.log for more information"
            )
        ready_servers.append(server)
    logging.debug("All servers are up!")
    toc = time.perf_counter()
    logging.debug(f"create_servers() Elapsed time: {toc - tic:0.4f}")
    return ready_servers


def create_cluster(
    servers: List[RedisServer],
    shard_count: int,
    replica_count: int,
    cluster_folder: str,
    use_tls: bool,
):
    tic = time.perf_counter()
    servers_tuple = (str(server) for server in servers)
    logging.debug("## Starting cluster creation...")
    p = subprocess.Popen(
        [
            "redis-cli",
            *get_redis_cli_option_args(cluster_folder, use_tls),
            "--cluster",
            "create",
            *servers_tuple,
            "--cluster-replicas",
            str(replica_count),
            "--cluster-yes",
        ],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
    )
    output, err = p.communicate(timeout=40)
    if err or "[OK] All 16384 slots covered." not in output:
        raise Exception(f"Failed to create cluster: {err if err else output}")

    wait_for_a_message_in_redis_logs(cluster_folder, "Cluster state changed: ok")
    wait_for_all_topology_views(servers, cluster_folder, use_tls)
    logging.debug("The cluster was successfully created!")
    toc = time.perf_counter()
    logging.debug(f"create_cluster {cluster_folder} Elapsed time: {toc - tic:0.4f}")


def create_standalone_replication(
    servers: List[RedisServer],
    cluster_folder: str,
    use_tls: bool,
):
    # Sets up replication among Redis servers, making them replicas of the primary server.
    tic = time.perf_counter()
    primary_server = servers[0]

    logging.debug("## Starting replication setup...")

    for i, server in enumerate(servers):
        if i == 0:
            continue  # Skip the primary server
        replica_of_command = [
            "redis-cli",
            *get_redis_cli_option_args(cluster_folder, use_tls),
            "-h",
            str(server.host),
            "-p",
            str(server.port),
            "REPLICAOF",
            str(primary_server.host),
            str(primary_server.port),
        ]
        p = subprocess.Popen(
            replica_of_command,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
        )
        output, err = p.communicate(timeout=20)
        if err or "OK" not in output:
            raise Exception(
                f"Failed to set up replication for server {server}: {err if err else output}"
            )
    servers_ports = [str(server.port) for server in servers]
    wait_for_a_message_in_redis_logs(
        cluster_folder,
        "sync: Finished with success",
        servers_ports[1:],
    )
    logging.debug(
        f"{len(servers) - 1} nodes successfully became replicas of the primary {primary_server}!"
    )

    toc = time.perf_counter()
    logging.debug(f"create_replication Elapsed time: {toc - tic:0.4f}")


def wait_for_a_message_in_redis_logs(
    cluster_folder: str,
    message: str,
    server_ports: Optional[List[str]] = None,
):
    for dir in Path(cluster_folder).rglob("*"):
        if not dir.is_dir():
            continue
        log_file = f"{dir}/redis.log"

        if server_ports and os.path.basename(os.path.normpath(dir)) not in server_ports:
            continue
        if not wait_for_message(log_file, message, 10):
            raise Exception(
                f"During the timeout duration, the server logs associated with port {dir} did not contain the message:{message}."
                f"See {dir}/redis.log for more information"
            )


def wait_for_all_topology_views(
    servers: List[RedisServer], cluster_folder: str, use_tls: bool
):
    """
    Wait for each of the nodes to have a topology view that contains all nodes.
    Only when a replica finished syncing and loading, it will be included in the CLUSTER SLOTS output.
    """
    for server in servers:
        cmd_args = [
            "redis-cli",
            "-h",
            server.host,
            "-p",
            str(server.port),
            *get_redis_cli_option_args(cluster_folder, use_tls),
            "cluster",
            "slots",
        ]
        logging.debug(f"Executing: {cmd_args}")
        retries = 60
        output = ""
        while retries >= 0:
            try:
                p = subprocess.Popen(
                    cmd_args,
                    stdout=subprocess.PIPE,
                    stderr=subprocess.PIPE,
                    text=True,
                )
                output, err = p.communicate(timeout=5)
                if err:
                    raise Exception(
                        f"Failed to execute command: {str(p.args)}\n Return code: {p.returncode}\n Error: {err}"
                    )

                if output.count(f"{server.host}") == len(servers):
                    logging.debug(f"Server {server} is ready!")
                    break
                else:
                    retries -= 1
                    time.sleep(0.5)
                    continue
            except subprocess.TimeoutExpired:
                time.sleep(0.5)
                retries -= 1
        if retries < 0:
            raise Exception(
                f"Timeout exceeded trying to wait for server {server} to know all hosts.\n"
                f"Current CLUSTER SLOTS output:\n{output}"
            )


def wait_for_server(
    server: RedisServer,
    cluster_folder: str,
    use_tls: bool,
    timeout: int = 10,
):
    logging.debug(f"Waiting for server: {server}")
    timeout_start = time.time()
    while time.time() < timeout_start + timeout:
        p = subprocess.Popen(
            [
                "redis-cli",
                "-h",
                server.host,
                "-p",
                str(server.port),
                *get_redis_cli_option_args(cluster_folder, use_tls),
                "PING",
            ],
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
        )
        try:
            output, err = p.communicate(timeout=1)
            if output.strip() == "PONG":
                logging.debug(f"Server {server} is up!")
                return True
            if p.returncode != 0:
                logging.debug(
                    f"Got error while waiting for server. Executed command: {str(p.args)}\n "
                    f"Return code: {p.returncode}\n Error: {err}"
                )
        except subprocess.TimeoutExpired:
            pass
        time.sleep(0.1)
    return False


def wait_for_message(
    log_file: str,
    message: str,
    timeout: int = 5,
):
    logging.debug(f"checking state changed in {log_file}")
    timeout_start = time.time()
    while time.time() < timeout_start + timeout:
        with open(log_file, "r") as f:
            redis_log = f.read()
            if message in redis_log:
                return True
            else:
                time.sleep(0.1)
                continue
    logging.warn(f"Timeout exceeded trying to check if {log_file} contains {message}")
    return False


def is_address_already_in_use(
    server: RedisServer,
    log_file: str,
    timeout: int = 5,
):
    logging.debug(f"checking is address already bind for: {server}")
    timeout_start = time.time()
    while time.time() < timeout_start + timeout:
        with open(log_file, "r") as f:
            redis_log = f.read()
            if "Address already in use" in redis_log:
                logging.debug(f"Address is already bind for server {server}")
                return True
            elif "Ready" in redis_log:
                logging.debug(f"Address is free for server {server}!")
                return False
            else:
                time.sleep(0.1)
                continue
    logging.warn(
        f"Timeout exceeded trying to check if address already in use for server {server}!"
    )
    return False


def dir_path(path: str):
    try:
        # Try to create the path folder if it isn't exist
        Path(path).mkdir(exist_ok=True)
    except Exception:
        pass
    if os.path.isdir(path):
        return path
    else:
        raise NotADirectoryError(path)


def stop_server(server: RedisServer, cluster_folder: str, use_tls: bool, auth: str):
    logging.debug(f"Stopping server {server}")
    cmd_args = [
        "redis-cli",
        "-h",
        server.host,
        "-p",
        str(server.port),
        *get_redis_cli_option_args(cluster_folder, use_tls, auth),
        "shutdown",
        "nosave",
    ]
    logging.debug(f"Executing: {cmd_args}")
    retries = 3
    raise_err = None
    while retries >= 0:
        try:
            p = subprocess.Popen(
                cmd_args,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True,
            )
            output, err = p.communicate(timeout=5)
            if err and "Warning: Using a password with '-a'" not in err:
                err_msg = (
                    f"Failed to shutdown host {server.host}:{server.port}:\n {err}"
                )
                logging.error(err_msg)
                raise Exception(
                    f"Failed to execute command: {str(p.args)}\n Return code: {p.returncode}\n Error: {err}"
                )
            if not wait_for_server_shutdown(server, cluster_folder, use_tls, auth):
                err_msg = "Timeout elapsed while waiting for the node to shutdown"
                logging.error(err_msg)
                raise Exception(err_msg)
            return
        except subprocess.TimeoutExpired as e:
            raise_err = e
            retries -= 1
    err_msg = f"Failed to shutdown host {server.host}:{server.port}: {raise_err}"
    logging.error(err_msg)
    raise Exception(err_msg)


def wait_for_server_shutdown(
    server: RedisServer,
    cluster_folder: str,
    use_tls: bool,
    auth: str,
    timeout: int = 20,
):
    logging.debug(f"Waiting for server {server} to shutdown")
    timeout_start = time.time()
    verify_times = 2
    while time.time() < timeout_start + timeout:
        p = subprocess.Popen(
            [
                "redis-cli",
                "-h",
                server.host,
                "-p",
                str(server.port),
                *get_redis_cli_option_args(cluster_folder, use_tls, auth),
                "PING",
            ],
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
        )
        try:
            output, err = p.communicate(timeout=1)
            if output.strip() == "PONG":
                logging.debug(f"Server {server} is still up")
            if p.returncode != 0:
                verify_times -= 1
                if verify_times == 0:
                    logging.debug(f"Success: server is down: {err}")
                    return True
        except subprocess.TimeoutExpired:
            logging.error("Sending ping to server during shutdown timed out")
            pass
        time.sleep(0.1)
    return False


def remove_folder(folder_path: str):
    logging.debug(f"Removing folder {folder_path}")
    p = subprocess.Popen(
        [
            "rm",
            "-rf",
            folder_path,
        ],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
    )
    output, err = p.communicate(timeout=3)
    if p.returncode != 0:
        raise Exception(
            f"Failed to execute command: {str(p.args)}\n Return code: {p.returncode}\n Error: {err}"
        )
    logging.debug(f"Folder {folder_path} removed")


def stop_clusters(
    host: str,
    folder_path: Optional[str],
    prefix: Optional[str],
    cluster_folder: Optional[str],
    use_tls: bool,
    auth: str,
    logfile: Optional[str],
    keep_folder: bool,
):
    if cluster_folder:
        cluster_folders = [cluster_folder]
    else:
        cluster_folders = [
            os.path.abspath(f"{folder_path}/{dirname}")
            for dirname in os.listdir(folder_path)
            if os.path.isdir(f"{folder_path}/{dirname}")
            and prefix is not None
            and dirname.startswith(prefix)
        ]
    for folder in cluster_folders:
        stop_cluster(host, folder, use_tls, auth, logfile, keep_folder)


def stop_cluster(
    host: str,
    cluster_folder: str,
    use_tls: bool,
    auth: str,
    logfile: Optional[str],
    keep_folder: bool,
):
    logfile = f"{cluster_folder}/cluster_manager.log" if not logfile else logfile
    init_logger(logfile)
    logging.debug(f"## Stopping cluster in path {cluster_folder}")
    for it in os.scandir(cluster_folder):
        if it.is_dir() and it.name.isdigit():
            port = it.name
            stop_server(RedisServer(host, int(port)), cluster_folder, use_tls, auth)
    logging.debug("All hosts were stopped")
    if not keep_folder:
        remove_folder(cluster_folder)


def main():
    parser = argparse.ArgumentParser(description="Cluster manager tool")
    parser.add_argument(
        "-H",
        "--host",
        type=str,
        help="Host address (default: %(default)s)",
        required=False,
        default="127.0.0.1",
    )

    parser.add_argument(
        "--tls",
        default=False,
        action="store_true",
        help="TLS enabled (default: %(default)s)",
    )

    parser.add_argument(
        "--auth",
        default=None,
        type=str,
        help="Pass authentication password (default: %(default)s)",
    )

    parser.add_argument(
        "-log",
        "--loglevel",
        dest="log",
        default="info",
        help="Provide logging level. Example --loglevel debug (default: %(default)s)",
    )

    parser.add_argument(
        "--logfile",
        help="Provide path to log file. (defaults to the cluster folder)",
    )

    subparsers = parser.add_subparsers(
        help="Tool actions",
        dest="action",
    )

    # Start parser
    parser_start = subparsers.add_parser("start", help="Start a new cluster")
    parser_start.add_argument(
        "--cluster-mode",
        action="store_true",
        help="Create a Redis Cluster with cluster mode enabled. If not specified, a Standalone Redis cluster will be created.",
        required=False,
    )
    parser_start.add_argument(
        "--folder-path",
        type=dir_path,
        help="Path to create the cluster main folder (defaults to: %(default)s)",
        required=False,
        default=CLUSTERS_FOLDER,
    )
    parser_start.add_argument(
        "-p",
        "--ports",
        nargs="+",
        type=int,
        help="List of ports to use for the new cluster."
        "The number of ports must be equal to the total number of nodes in the cluster",
        required=False,
    )

    parser_start.add_argument(
        "-n",
        "--shard-count",
        type=int,
        help="This option is only supported when used together with the --cluster-mode option."
        "It sets the number of cluster shards (default: %(default)s).",
        default=3,
        required=False,
    )

    parser_start.add_argument(
        "-r",
        "--replica-count",
        type=int,
        help="Number of replicas in each shard (default: %(default)s)",
        default=1,
        required=False,
    )

    parser_start.add_argument(
        "--prefix",
        type=str,
        help="Prefix to be used for the cluster folder name "
        "(default without TLS: %(default)s, default with TLS: tls-%(default)s)",
        default="redis-cluster",
        required=False,
    )

    parser_start.add_argument(
        "--load-module",
        action="append",
        help="The paths of the redis modules to load.",
        required=False,
    )

    # Stop parser
    parser_stop = subparsers.add_parser("stop", help="Shutdown a running cluster")
    parser_stop.add_argument(
        "--folder-path",
        type=dir_path,
        help="The folder path to stop all clusters with a prefix (defaults to: %(default)s) "
        "Only acceptable with '--prefix'",
        required=False,
        default=CLUSTERS_FOLDER,
    )
    parser_stop.add_argument(
        "--prefix",
        type=str,
        help="Stop all clusters that starts with this prefix in the given --folder-path. "
        "If --folder-path is passed, will search in the current directory",
        required=False,
    )
    parser_stop.add_argument(
        "--cluster-folder",
        type=str,
        help="Stop the cluster in the specified folder path. Expects a relative or a full path",
        required=False,
    )
    parser_stop.add_argument(
        "--keep-folder",
        action="store_true",
        default=False,
        help="Keep the cluster folder (default: %(default)s)",
        required=False,
    )

    args = parser.parse_args()
    # Check logging level

    level = LOG_LEVELS.get(args.log.lower())
    if level is None:
        raise parser.error(
            f"log level given: {args.log}"
            f" -- must be one of: {' | '.join(LOG_LEVELS.keys())}"
        )
    logging.root.setLevel(level=level)
    logging.info(f"## Executing cluster_manager.py with the following args:\n  {args}")

    if args.action == "start":
        if not args.cluster_mode:
            args.shard_count = 1
        if args.ports and len(args.ports) != args.shard_count * (
            1 + args.replica_count
        ):
            raise parser.error(
                f"The number of ports must be equal to the total number of nodes. "
                f"Number of passed ports == {len(args.ports)}, "
                f"number of nodes == {args.shard_count * (1 + args.replica_count)}"
            )
        tic = time.perf_counter()
        cluster_prefix = f"tls-{args.prefix}" if args.tls else args.prefix
        cluster_folder = create_cluster_folder(args.folder_path, cluster_prefix)
        logging.info(
            f"{datetime.now(timezone.utc)} Starting script for cluster {cluster_folder}"
        )
        logfile = (
            f"{cluster_folder}/cluster_manager.log"
            if not args.logfile
            else args.logfile
        )
        init_logger(logfile)
        servers = create_servers(
            args.host,
            args.shard_count,
            args.replica_count,
            args.ports,
            cluster_folder,
            args.tls,
            args.cluster_mode,
            args.load_module,
        )
        if args.cluster_mode:
            # Create a cluster
            create_cluster(
                servers,
                args.shard_count,
                args.replica_count,
                cluster_folder,
                args.tls,
            )
        elif args.replica_count > 0:
            # Create a standalone replication group
            create_standalone_replication(
                servers,
                cluster_folder,
                args.tls,
            )
        servers_str = ",".join(str(server) for server in servers)
        toc = time.perf_counter()
        logging.info(
            f"Created {'Cluster Redis' if args.cluster_mode else 'Standalone Redis'} in {toc - tic:0.4f} seconds"
        )
        print(f"CLUSTER_FOLDER={cluster_folder}")
        print(f"CLUSTER_NODES={servers_str}")

    elif args.action == "stop":
        if args.cluster_folder and args.prefix:
            raise parser.error(
                "--cluster-folder cannot be passed together with --prefix"
            )
        if not args.cluster_folder and not args.prefix:
            raise parser.error(
                "One of following arguments is required: --cluster-folder or --prefix"
            )
        tic = time.perf_counter()
        logging.info(
            f"{datetime.now(timezone.utc)} Stopping script for cluster/s {args.cluster_folder or f'{args.prefix}*'} in {args.folder_path}"
        )

        stop_clusters(
            args.host,
            args.folder_path,
            args.prefix,
            args.cluster_folder,
            args.tls,
            args.auth,
            args.logfile,
            args.keep_folder,
        )
        toc = time.perf_counter()
        logging.info(f"Cluster stopped in {toc - tic:0.4f} seconds")


if __name__ == "__main__":
    main()
