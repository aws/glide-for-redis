#!/usr/bin/python3

# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

import argparse
import fcntl
import json
import logging
import os
import random
import re
import signal
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

GLIDE_HOME_DIR = os.getenv("GLIDE_HOME_DIR") or f"{__file__}/.."

# Use /tmp/clusters for Windows WSL, otherwise use the default path
def _get_clusters_folder():
    if os.getenv("CLUSTERS_FOLDER"):
        return os.getenv("CLUSTERS_FOLDER")

    # Check if running on Windows WSL
    try:
        with open("/proc/version", "r") as f:
            if "microsoft" in f.read().lower():
                return "/tmp/clusters"
    except (FileNotFoundError, PermissionError):
        pass

    return os.path.abspath(f"{GLIDE_HOME_DIR}/clusters")

# TLS and mTLS certificates.
CLUSTERS_FOLDER = _get_clusters_folder()
TLS_FOLDER = os.path.abspath(f"{GLIDE_HOME_DIR}/tls_crts")

CA_CERTIFICATE_PATH = f"{TLS_FOLDER}/ca.crt"
CA_KEY_PATH = f"{TLS_FOLDER}/ca.key"
CA_SERIAL_PATH = f"{TLS_FOLDER}/ca.txt"
OPENSSL_CONFIG_PATH = f"{TLS_FOLDER}/openssl.cnf"
SERVER_CERTIFICATE_PATH = f"{TLS_FOLDER}/server.crt"
SERVER_CSR_PATH = f"{TLS_FOLDER}/server.csr"
SERVER_KEY_PATH = f"{TLS_FOLDER}/server.key"

# Timeout for calls to `openssl`.
OPENSSL_TIMEOUT_SECONDS = 30

# Allowed hostname for TLS certificate.
HOSTNAME_TLS: str = "valkey.glide.test.tls.com"

# Default hosts (loopback addresses for IPv4 and IPv6)
DEFAULT_HOST_IPV4: str = "127.0.0.1"
DEFAULT_HOST_IPV6: str = "::1"

# The `openssl` configuration for generating certificates. Defined after the
# host constants above because it interpolates them.
OPENSSL_CONFIG_CONTENTS = f"""\
keyUsage = digitalSignature, keyEncipherment
subjectAltName = IP:{DEFAULT_HOST_IPV4}, IP:{DEFAULT_HOST_IPV6}, DNS:localhost, DNS:{HOSTNAME_TLS}
"""


def get_command(commands: List[str]) -> str:
    for command in commands:
        try:
            result = subprocess.run(
                ["which", command],
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True,
            )
            if result.returncode == 0:
                return command
        except Exception as e:
            logging.error(f"Error checking {command}: {e}")
    raise Exception(f"Neither {' nor '.join(commands)} found in the system.")


# Global variables for caching server commands (set lazily)
_SERVER_COMMAND = None
_CLI_COMMAND = None


def get_server_command() -> str:
    """Get server command, checking valkey-server first, then redis-server"""
    global _SERVER_COMMAND
    if _SERVER_COMMAND is None:
        _SERVER_COMMAND = get_command(["valkey-server", "redis-server"])
    return _SERVER_COMMAND


def get_cli_command() -> str:
    """Get CLI command, checking valkey-cli first, then redis-cli"""
    global _CLI_COMMAND
    if _CLI_COMMAND is None:
        _CLI_COMMAND = get_command(["valkey-cli", "redis-cli"])
    return _CLI_COMMAND


def init_logger(logfile: str):
    print(f"LOG_FILE={logfile}")
    root_logger = logging.getLogger()
    handler = logging.FileHandler(logfile, "w", "utf-8")
    root_logger.addHandler(handler)


def _verify_tls_certs() -> bool:
    """Whether a complete, valid TLS certificate set exists."""
    for cert in [CA_CERTIFICATE_PATH, SERVER_CERTIFICATE_PATH, SERVER_KEY_PATH]:
        if not os.path.exists(cert):
            return False

    # Verify the server certificate parses, was signed by
    # the CA certificate, and is within its validity dates.
    verify_certs = subprocess.run(
        ["openssl", "verify", "-CAfile", CA_CERTIFICATE_PATH, SERVER_CERTIFICATE_PATH],
        capture_output=True,
        text=True,
    )

    if verify_certs.returncode != 0:
        return False

    # Verify the server key parses.
    key_public_key = subprocess.run(
        ["openssl", "pkey", "-in", SERVER_KEY_PATH, "-pubout"],
        capture_output=True,
        text=True,
    )

    if key_public_key.returncode != 0:
        return False

    # Verify that the server key matches the server certificate.
    cert_public_key = subprocess.run(
        ["openssl", "x509", "-in", SERVER_CERTIFICATE_PATH, "-noout", "-pubkey"],
        capture_output=True,
        text=True,
    )

    if (
        cert_public_key.returncode != 0
        or cert_public_key.stdout != key_public_key.stdout
    ):
        return False

    return True


def _run_openssl(args: List[str]) -> None:
    """Run `openssl <args>`, raising on failure."""
    result = subprocess.run(
        ["openssl", *args],
        capture_output=True,
        text=True,
        timeout=OPENSSL_TIMEOUT_SECONDS,
    )

    if result.returncode != 0:
        raise Exception(f"openssl {' '.join(args)} failed:\n{result.stderr}")


def generate_tls_certs():
    """Generate the shared TLS certificates if needed."""
    Path(TLS_FOLDER).mkdir(exist_ok=True)

    # Blocks until any concurrent process releases the lock.
    with open(f"{TLS_FOLDER}/.certs-lock", "w") as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)

        if _verify_tls_certs():
            return

        # Based on shell script in valkey's server tests
        # https://github.com/valkey-io/valkey/blob/0d2ba9b94d28d4022ea475a2b83157830982c941/utils/gen-test-certs.sh
        logging.debug("## Generating TLS certificates")
        tic = time.perf_counter()

        with open(OPENSSL_CONFIG_PATH, "w") as f:
            f.write(OPENSSL_CONFIG_CONTENTS)

        # Build CA and server keys.
        _run_openssl(["genrsa", "-out", CA_KEY_PATH, "2048"])
        _run_openssl(["genrsa", "-out", SERVER_KEY_PATH, "2048"])

        # Build CA certificate.
        _run_openssl(
            [
                "req",
                "-x509",
                "-new",
                "-nodes",
                "-sha256",
                "-key",
                CA_KEY_PATH,
                "-days",
                "3650",
                "-subj",
                "/O=Valkey GLIDE Test/CN=Certificate Authority",
                "-out",
                CA_CERTIFICATE_PATH,
            ]
        )

        # Build server certificate signing request (CSR).
        _run_openssl(
            [
                "req",
                "-new",
                "-sha256",
                "-subj",
                "/O=Valkey GLIDE Test/CN=Generic-cert",
                "-key",
                SERVER_KEY_PATH,
                "-out",
                SERVER_CSR_PATH,
            ]
        )

        # Sign the CSR with the CA to produce the server certificate.
        _run_openssl(
            [
                "x509",
                "-req",
                "-sha256",
                "-in",
                SERVER_CSR_PATH,
                "-CA",
                CA_CERTIFICATE_PATH,
                "-CAkey",
                CA_KEY_PATH,
                "-CAserial",
                CA_SERIAL_PATH,
                "-CAcreateserial",
                "-days",
                "3650",
                "-extfile",
                OPENSSL_CONFIG_PATH,
                "-out",
                SERVER_CERTIFICATE_PATH,
            ]
        )

        toc = time.perf_counter()
        logging.debug(f"generate_tls_certs() Elapsed time: {toc - tic:0.4f}")
        logging.debug(f"TLS files= {SERVER_CERTIFICATE_PATH}, {SERVER_KEY_PATH}, {CA_CERTIFICATE_PATH}")


def get_cli_option_args(
    cluster_folder: str, use_tls: bool, auth: Optional[str] = None,
    tls_cert_file: Optional[str] = None,
    tls_key_file: Optional[str] = None,
    tls_ca_cert_file: Optional[str] = None,
) -> List[str]:
    args = (
        [
            "--tls",
            "--cert",
            tls_cert_file or SERVER_CERTIFICATE_PATH,
            "--key",
            tls_key_file or SERVER_KEY_PATH,
            "--cacert",
            tls_ca_cert_file or CA_CERTIFICATE_PATH,
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


class Server:
    def __init__(self, host: str, port: int) -> None:
        self.host = host
        self.port = port
        self.pid = -1
        self.is_primary = True

    def __str__(self) -> str:
        return f"{self.host}:{self.port}"

    def process_id(self) -> int:
        return self.pid

    def set_process_id(self, pid: int):
        self.pid = pid

    def to_dictionary(self) -> dict:
        return {
            "host": self.host,
            "port": self.port,
            "pid": self.pid,
            "is_primary": self.is_primary,
        }

    def set_primary(self, is_primary: bool):
        self.is_primary = is_primary


def print_servers_json(servers: List[Server]):
    """
    Print the list of servers to the stdout as JSON array
    """
    arr = []
    for server in servers:
        arr.append(server.to_dictionary())

    print("SERVERS_JSON={}".format(json.dumps(arr)))


def next_free_port(
    min_port: int = 6379, max_port: int = 55535, timeout: int = 60
) -> int:
    tic = time.perf_counter()

    timeout_start = time.time()
    while time.time() < timeout_start + timeout:
        try:
            port = random.randint(min_port, max_port)

            # Check IPv4 address.
            sock4 = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            sock4.bind((DEFAULT_HOST_IPV4, port))
            sock4.close()

            # Check IPv6 address.
            sock6 = socket.socket(socket.AF_INET6, socket.SOCK_STREAM)
            sock6.setsockopt(socket.IPPROTO_IPV6, socket.IPV6_V6ONLY, 1)
            sock6.bind((DEFAULT_HOST_IPV6, port))
            sock6.close()

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


def start_server(
    host: str,
    port: Optional[int],
    cluster_folder: str,
    tls: bool,
    tls_args: List[str],
    cluster_mode: bool,
    load_module: Optional[List[str]] = None,
) -> Tuple[Server, str]:
    port = port if port else next_free_port()
    logging.debug(f"Creating server {host}:{port}")

    # Create sub-folder for each node
    node_folder = f"{cluster_folder}/{port}"
    Path(node_folder).mkdir(exist_ok=True)

    def get_server_version(server_name):
        result = subprocess.run(
            [server_name, "--version"], capture_output=True, text=True
        )
        version_output = result.stdout
        version_match = re.search(
            r"server v=(\d+\.\d+\.\d+)", version_output, re.IGNORECASE
        )
        if version_match:
            return tuple(map(int, version_match.group(1).split(".")))
        raise Exception("Unable to determine server version.")

    server_name = get_server_command()
    server_version = get_server_version(server_name)
    logfile = f"{node_folder}/redis.log"

    # Define command arguments
    logfile = f"{node_folder}/server.log"
    cmd_args = [
        get_server_command(),
        f"{'--tls-port' if tls else '--port'}",
        str(port),
        "--cluster-enabled",
        f"{'yes' if cluster_mode else 'no'}",
        "--dir",
        node_folder,
        "--daemonize",
        "yes",
        "--logfile",
        logfile,
        "--protected-mode",
        "no",
        "--appendonly",
        "no",
        "--save",
        "",
    ]

    # Bind server to both IPv4 and IPv6 loopback addresses.
    cmd_args.extend(["--bind", DEFAULT_HOST_IPV4, DEFAULT_HOST_IPV6])

    # If host is a DNS hostname, set cluster-announce-hostname so
    # the cluster topology reports DNS names instead of IP addresses.
    if cluster_mode:
        try:
            import ipaddress
            ipaddress.ip_address(host)
        except ValueError:
            cmd_args.extend(["--cluster-announce-hostname", host])

    if server_version >= (7, 0, 0):
        cmd_args.extend(["--enable-debug-command", "yes"])
    # Enable multi-database support in cluster mode for Valkey 9.0+
    if cluster_mode and server_version >= (9, 0, 0):
        cmd_args.extend(["--cluster-databases", "16"])
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

    server = Server(host, port)

    # Read the process ID from the log file
    # Note that `p.pid` is not good here since we daemonize the process
    process_id = wait_for_regex_in_log(
        logfile, r"version=(.*?)pid=([\d]+), just started", 2
    )
    if process_id:
        server.set_process_id(int(process_id))

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
    json_output: bool = False,
    tls_cert_file: Optional[str] = None,
    tls_key_file: Optional[str] = None,
    tls_ca_cert_file: Optional[str] = None,
    tls_auth_clients: bool = False,
) -> List[Server]:
    tic = time.perf_counter()
    logging.debug("## Creating servers")
    ready_servers: List[Server] = []
    nodes_count = shard_count * (1 + replica_count)
    tls_args = []

    if tls is True:
        custom_tls_files = (tls_cert_file, tls_key_file, tls_ca_cert_file)

        # Generate default TLS files if not provided.
        if not any(custom_tls_files):
            generate_tls_certs()
            cert_file = SERVER_CERTIFICATE_PATH
            key_file = SERVER_KEY_PATH
            ca_file = CA_CERTIFICATE_PATH

        # Verify that all of the TLS files are provided.
        # We do not support mixing custom and default TLS files.
        elif not all(custom_tls_files):
            raise ValueError("TLS certificate, key, and CA certificate must be provided together")

        else:
            cert_file = tls_cert_file
            key_file = tls_key_file
            ca_file = tls_ca_cert_file

        tls_args = [
            "--tls-cluster",
            "yes",
            "--tls-cert-file",
            cert_file,
            "--tls-key-file",
            key_file,
            "--tls-ca-cert-file",
            ca_file,
            # When true, the server requires a client certificate on every TLS connection.
            "--tls-auth-clients",
            "yes" if tls_auth_clients else "no",
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
            start_server(
                host, port, cluster_folder, tls, tls_args, cluster_mode, load_module
            )
        )
    # Check all servers
    while len(servers_to_check) > 0:
        server, node_folder = servers_to_check.pop()
        logging.debug(f"Checking server {server.host}:{server.port}")
        if is_address_already_in_use(server, f"{node_folder}/server.log"):
            remove_folder(node_folder)
            if ports is not None:
                # The user passed a taken port, exit with an error
                raise Exception(
                    f"Couldn't start server on {server.host}:{server.port}, address already in use"
                )
            # The port was already taken, try to find a new free one
            servers_to_check.add(
                start_server(
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
        if not wait_for_server(server, cluster_folder, tls, 10, tls_cert_file, tls_key_file, tls_ca_cert_file):
            raise Exception(
                f"Waiting for server {server.host}:{server.port} to start exceeded timeout.\n"
                f"See {node_folder}/server.log for more information"
            )
        ready_servers.append(server)
    logging.debug("All servers are up!")
    toc = time.perf_counter()
    logging.debug(f"create_servers() Elapsed time: {toc - tic:0.4f}")
    return ready_servers


def create_cluster(
    servers: List[Server],
    shard_count: int,
    replica_count: int,
    cluster_folder: str,
    use_tls: bool,
    tls_cert_file: Optional[str] = None,
    tls_key_file: Optional[str] = None,
    tls_ca_cert_file: Optional[str] = None,
):
    tic = time.perf_counter()
    servers_tuple = (str(server) for server in servers)
    logging.debug("## Starting cluster creation...")
    p = subprocess.Popen(
        [
            get_cli_command(),
            *get_cli_option_args(cluster_folder, use_tls, None, tls_cert_file, tls_key_file, tls_ca_cert_file),
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

    wait_for_a_message_in_logs(cluster_folder, "Cluster state changed: ok")
    wait_for_all_topology_views(servers, cluster_folder, use_tls, tls_cert_file, tls_key_file, tls_ca_cert_file)
    print_servers_json(servers)

    logging.debug("The cluster was successfully created!")
    toc = time.perf_counter()
    logging.debug(f"create_cluster {cluster_folder} Elapsed time: {toc - tic:0.4f}")


def create_standalone_replication(
    servers: List[Server],
    cluster_folder: str,
    use_tls: bool,
):
    # Sets up replication among servers, making them replicas of the primary server.
    tic = time.perf_counter()
    primary_server = servers[0]

    logging.debug("## Starting replication setup...")

    for i, server in enumerate(servers):
        if i == 0:
            continue  # Skip the primary server
        replica_of_command = [
            get_cli_command(),
            *get_cli_option_args(cluster_folder, use_tls),
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
    wait_for_a_message_in_logs(
        cluster_folder,
        "sync: Finished with success",
        servers_ports[1:],
    )
    logging.debug(
        f"{len(servers) - 1} nodes successfully became replicas of the primary {primary_server}!"
    )

    toc = time.perf_counter()
    logging.debug(f"create_replication Elapsed time: {toc - tic:0.4f}")


def wait_for_a_message_in_logs(
    cluster_folder: str,
    message: str,
    server_ports: Optional[List[str]] = None,
):
    for dir in Path(cluster_folder).rglob("*"):
        if not dir.is_dir():
            continue
        log_file = f"{dir}/server.log"

        if server_ports and os.path.basename(os.path.normpath(dir)) not in server_ports:
            continue
        if not wait_for_message(log_file, message, 10):
            raise Exception(
                f"During the timeout duration, the server logs associated with port {dir} did not contain the message:{message}."
                f"See {dir}/server.log for more information"
            )


def parse_cluster_nodes(command_output: Optional[str]) -> Optional[dict]:
    """
    Parameters
    ----------
    command_output: str :
        The output returned from valkey for the command 'CLUSTER NODES'

    Returns
    -------
        A dictionary for the current node's details
    """
    if command_output is None:
        return None

    lines = command_output.splitlines(keepends=False)
    for line in lines:
        tokens = line.split(" ")
        if len(tokens) < 3:
            continue

        node_id = tokens[0].strip()
        network = tokens[1].strip()
        flags = tokens[2].strip()

        if "myself" in flags:
            # This is us
            return {
                "node_id": node_id,
                "network": network,
                "is_primary": "master" in flags,
            }
    return None


def redis_cli_run_command(cmd_args: List[str]) -> Optional[str]:
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
        return output
    except subprocess.TimeoutExpired:
        return None


def wait_for_all_topology_views(
    servers: List[Server],
    cluster_folder: str,
    use_tls: bool,
    tls_cert_file: Optional[str] = None,
    tls_key_file: Optional[str] = None,
    tls_ca_cert_file: Optional[str] = None,
):
    """
    Wait for each of the nodes to have a topology view that contains all nodes.
    Only when a replica finished syncing and loading, it will be included in the CLUSTER SLOTS output.
    """
    for server in servers:
        cmd_args = [
            get_cli_command(),
            "-h",
            server.host,
            "-p",
            str(server.port),
            *get_cli_option_args(cluster_folder, use_tls, None, tls_cert_file, tls_key_file, tls_ca_cert_file),
            "cluster",
            "slots",
        ]
        logging.debug(f"Executing: {cmd_args}")
        retries = 80
        while retries >= 0:
            output = redis_cli_run_command(cmd_args)
            if output is not None and output.count(f"{server.host}") == len(servers):
                # Server is ready, get the node's role
                cmd_args = [
                    get_cli_command(),
                    "-h",
                    server.host,
                    "-p",
                    str(server.port),
                    *get_cli_option_args(cluster_folder, use_tls, None, tls_cert_file, tls_key_file, tls_ca_cert_file),
                    "cluster",
                    "nodes",
                ]
                cluster_slots_output = redis_cli_run_command(cmd_args)
                node_info = parse_cluster_nodes(cluster_slots_output)
                if node_info:
                    server.set_primary(node_info["is_primary"])
                logging.debug(f"Server {server} is ready!")
                break
            else:
                retries -= 1
                time.sleep(1)
                continue

        if retries < 0:
            raise Exception(
                f"Timeout exceeded trying to wait for server {server} to know all hosts.\n"
                f"Current CLUSTER SLOTS output:\n{output}"
            )


def wait_for_server(
    server: Server,
    cluster_folder: str,
    use_tls: bool,
    timeout: int = 10,
    tls_cert_file: Optional[str] = None,
    tls_key_file: Optional[str] = None,
    tls_ca_cert_file: Optional[str] = None,
):
    logging.debug(f"Waiting for server: {server}")
    timeout_start = time.time()
    while time.time() < timeout_start + timeout:
        p = subprocess.Popen(
            [
                get_cli_command(),
                "-h",
                server.host,
                "-p",
                str(server.port),
                *get_cli_option_args(cluster_folder, use_tls, None, tls_cert_file, tls_key_file, tls_ca_cert_file),
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
            server_log = f.read()
            if message in server_log:
                return True
            else:
                time.sleep(0.1)
                continue
    logging.warn(f"Timeout exceeded trying to check if {log_file} contains {message}")
    return False


def wait_for_regex_in_log(
    logfile: str,
    pattern: str,
    group: int,
    timeout: int = 5,
) -> Optional[str]:
    """Read the log file and search for a regular expression 'pattern'. If match is found
    return the regex group identified by 'group'"""

    logging.debug(f"searching regex pattern: '{pattern}' in file: '{logfile}'")
    timeout_start = time.time()

    while time.time() < timeout_start + timeout:
        with open(logfile, "r") as f:
            content = f.read()
            lines = content.splitlines(keepends=False)
            for line in lines:
                result = re.search(pattern, line)
                if result:
                    return result.group(group)

            else:
                time.sleep(0.1)
                continue
    return None


def is_address_already_in_use(
    server: Server,
    log_file: str,
    timeout: int = 10,
):
    logging.debug(f"checking is address already bind for: {server}")
    timeout_start = time.time()
    address_in_use_errors = [
        "Address already in use",
        "Address in use",
        "address in use",
    ]
    while time.time() < timeout_start + timeout:
        if not os.path.exists(log_file):
            time.sleep(0.1)
            continue

        with open(log_file, "r") as f:
            server_log = f.read()
            # Check for known error message variants because different C libraries
            if any(error_msg in server_log for error_msg in address_in_use_errors):
                logging.debug(f"Address is already bind for server {server}")
                return True
            elif "Ready" in server_log:
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


def stop_server(server: Server, cluster_folder: str, use_tls: bool, auth: str):
    logging.debug(f"Stopping server {server}")
    try:
        ping = subprocess.run(
            [get_cli_command(), "-h", server.host, "-p", str(server.port),
             *get_cli_option_args(cluster_folder, use_tls, auth), "PING"],
            capture_output=True, text=True, timeout=2,
        )
        if ping.returncode != 0:
            logging.info(f"Server {server} is already down, skipping shutdown")
            return
    except subprocess.TimeoutExpired:
        logging.debug(f"Ping to {server} timed out, attempting shutdown anyway")
    cmd_args = [
        get_cli_command(),
        "-h",
        server.host,
        "-p",
        str(server.port),
        *get_cli_option_args(cluster_folder, use_tls, auth),
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
            if err and "Connection refused" in err:
                logging.info(f"Server {server} is already down")
                return
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
    server: Server,
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
                get_cli_command(),
                "-h",
                server.host,
                "-p",
                str(server.port),
                *get_cli_option_args(cluster_folder, use_tls, auth),
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
    pids: Optional[str],
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

    # request for graceful shutdown
    for folder in cluster_folders:
        stop_cluster(host, folder, use_tls, auth, logfile, keep_folder)

    if pids:
        pid_arr = pids.split(",")
        for pid in pid_arr:
            try:
                # Kill the process
                os.kill(int(pid), signal.SIGKILL)
            except ProcessLookupError as e:
                logging.debug(f"Could not kill server with PID: {pid}. {e}")
                pass


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
    all_stopped = True
    for it in os.scandir(cluster_folder):
        if it.is_dir() and it.name.isdigit():
            port = it.name
            try:
                stop_server(Server(host, int(port)), cluster_folder, use_tls, auth)
            except Exception:
                all_stopped = False
    if all_stopped:
        logging.debug("All hosts were stopped gracefully")

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
        default=DEFAULT_HOST_IPV4,
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
        default="cluster",
        required=False,
    )

    parser_start.add_argument(
        "--load-module",
        action="append",
        help="The paths of the server modules to load.",
        required=False,
    )

    parser_start.add_argument(
        "--tls-cert-file",
        type=str,
        help="Path to TLS certificate file (default: uses generated certificates)",
        required=False,
    )

    parser_start.add_argument(
        "--tls-key-file",
        type=str,
        help="Path to TLS key file (default: uses generated certificates)",
        required=False,
    )

    parser_start.add_argument(
        "--tls-ca-cert-file",
        type=str,
        help="Path to TLS CA certificate file (default: uses generated certificates)",
        required=False,
    )

    parser_start.add_argument(
        "--tls-auth-clients",
        action="store_true",
        default=False,
        help="Require a client certificate on every TLS connection, i.e. "
        "--tls-auth-clients yes (default: %(default)s)",
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

    parser_stop.add_argument(
        "--pids",
        type=str,
        help="Optionally, provide comma separated list of process IDs to terminate",
        default="",
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
            False,
            getattr(args, 'tls_cert_file', None),
            getattr(args, 'tls_key_file', None),
            getattr(args, 'tls_ca_cert_file', None),
            getattr(args, 'tls_auth_clients', False),
        )
        if args.cluster_mode:
            # Create a cluster
            create_cluster(
                servers,
                args.shard_count,
                args.replica_count,
                cluster_folder,
                args.tls,
                getattr(args, 'tls_cert_file', None),
                getattr(args, 'tls_key_file', None),
                getattr(args, 'tls_ca_cert_file', None),
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
        stop_prefix = f"tls-{args.prefix}" if args.tls and args.prefix else args.prefix
        logging.info(
            f"{datetime.now(timezone.utc)} Stopping script for cluster/s {args.cluster_folder or f'{stop_prefix}*'} in {args.folder_path}"
        )

        stop_clusters(
            args.host,
            args.folder_path,
            stop_prefix,
            args.cluster_folder,
            args.tls,
            args.auth,
            args.logfile,
            args.keep_folder,
            args.pids,
        )
        toc = time.perf_counter()
        logging.info(f"Cluster stopped in {toc - tic:0.4f} seconds")


if __name__ == "__main__":
    main()
