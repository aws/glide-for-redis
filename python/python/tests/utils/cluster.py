# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

import os
import subprocess
import sys
from typing import List, Optional, Union

from glide.config import NodeAddress

SCRIPT_FILE = os.path.abspath(f"{__file__}/../../../../../utils/cluster_manager.py")


class RedisCluster:
    def __init__(
        self,
        tls,
        cluster_mode: bool = False,
        shard_count: int = 3,
        replica_count: int = 1,
        load_module: Optional[List[str]] = None,
        addresses: Optional[List[List[str]]] = None,
    ) -> None:
        if addresses:
            self.init_from_existing_cluster(addresses)
        else:
            self.tls = tls
            args_list = [sys.executable, SCRIPT_FILE]
            if tls:
                args_list.append("--tls")
            args_list.append("start")
            if cluster_mode:
                args_list.append("--cluster-mode")
            if load_module:
                if len(load_module) == 0:
                    raise ValueError(
                        "Please provide the path(s) to the module(s) you want to load."
                    )
                for module in load_module:
                    args_list.extend(["--load-module", module])
            args_list.append(f"-n {shard_count}")
            args_list.append(f"-r {replica_count}")
            p = subprocess.Popen(
                args_list,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True,
            )
            output, err = p.communicate(timeout=40)
            if p.returncode != 0:
                raise Exception(f"Failed to create a cluster. Executed: {p}:\n{err}")
            self.parse_cluster_script_start_output(output)

    def parse_cluster_script_start_output(self, output: str):
        assert "CLUSTER_FOLDER" in output and "CLUSTER_NODES" in output
        lines_output = output.splitlines()
        for line in lines_output:
            if "CLUSTER_FOLDER" in line:
                splitted_line = line.split("CLUSTER_FOLDER=")
                assert len(splitted_line) == 2
                self.cluster_folder = splitted_line[1]
            if "CLUSTER_NODES" in line:
                nodes_list = []
                splitted_line = line.split("CLUSTER_NODES=")
                assert len(splitted_line) == 2
                nodes_addresses = splitted_line[1].split(",")
                assert len(nodes_addresses) > 0
                for addr in nodes_addresses:
                    host, port = addr.split(":")
                    nodes_list.append(NodeAddress(host, int(port)))
                self.nodes_addr = nodes_list

    def init_from_existing_cluster(self, addresses: List[List[str]]):
        self.cluster_folder = ""
        self.nodes_addr = []
        for [host, port] in addresses:
            self.nodes_addr.append(NodeAddress(host, int(port)))

    def __del__(self):
        if self.cluster_folder:
            args_list = [sys.executable, SCRIPT_FILE]
            if self.tls:
                args_list.append("--tls")
            args_list.extend(["stop", "--cluster-folder", self.cluster_folder])
            p = subprocess.Popen(
                args_list,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True,
            )
            output, err = p.communicate(timeout=20)
            if p.returncode != 0:
                raise Exception(
                    f"Failed to stop a cluster {self.cluster_folder}. Executed: {p}:\n{err}"
                )
