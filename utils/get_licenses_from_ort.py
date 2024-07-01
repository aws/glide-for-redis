# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

import json
import os
from typing import List, Set

"""
This script should be used after all specific langauge folders were scanned by the analyzer of the OSS review tool (ORT).
The analyzer tool reports to analyzer-result.json files, which the script expect to be found under the <language_folder>/ort_results path.
The script outputs a set of licenses identified by the analyzer. GLIDE maintainers should review the returned list to ensure that all licenses are approved.
"""

APPROVED_LICENSES = [
    "Unicode-DFS-2016",
    "(Apache-2.0 OR MIT) AND Unicode-DFS-2016",
    "0BSD OR Apache-2.0 OR MIT",
    "Apache-2.0",
    "Apache-2.0 AND (Apache-2.0 OR BSD-2-Clause)",
    "Apache-2.0 AND (Apache-2.0 OR BSD-3-Clause)",
    "Apache-2.0 OR Apache-2.0 WITH LLVM-exception OR MIT",
    "Apache-2.0 OR BSD-2-Clause OR MIT",
    "Apache-2.0 OR BSL-1.0",
    "Apache-2.0 OR ISC OR MIT",
    "Apache-2.0 OR MIT",
    "Apache-2.0 OR MIT OR Zlib",
    "Apache-2.0 WITH LLVM-exception",
    "BSD License",
    "BSD-2-Clause",
    "BSD-2-Clause OR Apache-2.0",
    "BSD-3-Clause",
    "BSD-3-Clause OR Apache-2.0",
    "ISC",
    "MIT",
    "Zlib",
    "MIT OR Unlicense",
    "PSF-2.0",
]


class OrtResults:
    def __init__(self, name: str, ort_results_folder: str) -> None:
        """
        Args:
            name (str): the language name.
            ort_results_folder (str): The relative path to the ort results folder from the root of the glide-for-redis directory.
        """
        script_path = os.path.dirname(os.path.realpath(__file__))
        folder_path = f"{script_path}/../{ort_results_folder}"
        self.analyzer_result_file = f"{folder_path}/analyzer-result.json"
        self.notice_file = f"{folder_path}/NOTICE_DEFAULT"
        self.name = name


class PackageLicense:
    def __init__(self, package_name: str, language: str, license: str) -> None:
        self.package_name = package_name
        self.language = language
        self.license = license

    def __str__(self):
        return f"Package_name: {self.package_name}, Language: {self.language}, License: {self.license}"


ort_results_per_lang = [
    OrtResults("Python", "python/ort_results"),
    OrtResults("Node", "node/ort_results"),
    OrtResults("Rust", "glide-core/ort_results"),
    OrtResults("Java", "java/ort_results"),
]

all_licenses_set: Set = set()
unknown_licenses: List[PackageLicense] = []

for ort_result in ort_results_per_lang:
    with open(ort_result.analyzer_result_file, "r") as ort_results, open(
        ort_result.notice_file, "r"
    ) as notice_file:
        json_file = json.load(ort_results)
        notice_file_text = notice_file.read()
        for package in json_file["analyzer"]["result"]["packages"]:
            package_name = package["id"].split(":")[2]
            if package_name not in notice_file_text:
                # skip packages not in the final report
                print(f"Skipping package {package_name}")
                continue
            try:
                for license in package["declared_licenses_processed"].values():
                    if isinstance(license, list) or isinstance(license, dict):
                        final_licenses = (
                            list(license.values())
                            if isinstance(license, dict)
                            else license
                        )
                    else:
                        final_licenses = [license]
                    for license in final_licenses:
                        if license not in APPROVED_LICENSES:
                            unknown_licenses.append(
                                PackageLicense(package["id"], ort_result.name, license)
                            )
                        all_licenses_set.add(license)
            except Exception:
                print(
                    f"Received error for package {package} used by {ort_result.name}\n Found license={license}"
                )
                raise

print("\n\n#### Found Licenses #####\n")
all_licenses_set = set(sorted(all_licenses_set))
for license in all_licenses_set:
    print(f"{license}")

print("\n\n#### unknown / Not Pre-Approved Licenses #####\n")
for package in unknown_licenses:
    print(str(package))
