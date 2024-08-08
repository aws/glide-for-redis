#!/bin/python3

# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

import csv
import json
import os
import sys

output_file_name = sys.argv[-1]
with open(output_file_name, "w+") as output_file:
    writer = csv.writer(output_file)
    base_fields = [
        "language",
        "client",
        "is_cluster",
        "num_of_tasks",
        "data_size",
        "client_count",
        "tps",
        "get_non_existing_p50_latency",
        "get_non_existing_p90_latency",
        "get_non_existing_p99_latency",
        "get_non_existing_average_latency",
        "get_non_existing_std_dev",
        "get_existing_p50_latency",
        "get_existing_p90_latency",
        "get_existing_p99_latency",
        "get_existing_average_latency",
        "get_existing_std_dev",
        "set_p50_latency",
        "set_p90_latency",
        "set_p99_latency",
        "set_average_latency",
        "set_std_dev",
    ]

    writer.writerow(base_fields)

    for json_file_full_path in sys.argv[1:-1]:
        with open(json_file_full_path) as file:
            json_objects = json.load(file)

            json_file_name = os.path.basename(json_file_full_path)

            languages = ["csharp", "node", "python", "rust", "java", "go"]
            language = next(
                (language for language in languages if language in json_file_name), None
            )

            if not language:
                raise Exception(f"Unknown language for {json_file_name}")
            for json_object in json_objects:
                json_object["language"] = language
                values = [json_object[field] for field in base_fields]
                writer.writerow(values)

for json_file_full_path in sys.argv[1:-1]:
    os.remove(json_file_full_path)
