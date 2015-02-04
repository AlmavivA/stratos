# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.

import pytest
import json
from .. cartridgeagent.modules.artifactmgt.repository import Repository
from .. cartridgeagent.modules.artifactmgt.git.agentgithandler import *


def test_clone():
    with open("conf/simple_repo.json", "r") as f:
        repo_string = f.read()

    repo_info = json.loads(repo_string, object_hook=repo_object_decoder)
    # test_info = json.loads(repo_string)
    sub_run, repo_context = AgentGitHandler.checkout(repo_info)

    assert sub_run, "Not detected as subscription run"

    assert os.path.isdir(repo_info.repo_path), "Local repository directory not created."

    output, errors = AgentGitHandler.execute_git_command(["status"], repo_info.repo_path)
    assert len(errors) == 0 and "nothing to commit, working directory clean" in output, "Git clone failed. "

# def test_auth_clone():
#     with open("conf/simple_repo.json", "r") as f:
#         repo_string = f.read()
#
#     repo_info = json.loads(repo_string, object_hook=repo_object_decoder)
#     # test_info = json.loads(repo_string)
#     sub_run, repo_context = AgentGitHandler.checkout(repo_info)
#
#     assert sub_run, "Not detected as subscription run"
#
#     assert os.path.isdir(repo_info.repo_path), "Local repository directory not created."
#
#     output, errors = AgentGitHandler.execute_git_command(["status"], repo_info.repo_path)
#     assert len(errors) == 0 and "nothing to commit, working directory clean" in output, "Git clone failed. "


# def test_push():
#     with open("conf/simple_repo.json", "r") as f:
#         repo_string = f.read()
#
#     repo_info = json.loads(repo_string, object_hook=repo_object_decoder)



def repo_object_decoder(obj):
    """ Repository object decoder from JSON
    :param obj: json object
    :return:
    """
    return Repository(obj["repoURL"], obj["repoUsername"], obj["repoPassword"], obj["repoPath"], obj["tenantId"], obj["multitenant"], obj["commitEnabled"])