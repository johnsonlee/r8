# Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import os
import utils

ANDROID_M_API = '23'

BASE = os.path.join(utils.THIRD_PARTY, 'youtube')

V17_19_BASE = os.path.join(BASE, 'youtube.android_17.19')
V17_19_PREFIX = os.path.join(V17_19_BASE, 'YouTubeRelease')

LATEST_VERSION = '17.19'

VERSIONS = {
    '17.19': {
        'deploy': {
            'sanitize_libraries':
                False,
            'inputs': ['%s_deploy.jar' % V17_19_PREFIX],
            'libraries': [
                os.path.join(
                    V17_19_BASE,
                    'legacy_YouTubeRelease_combined_library_jars_filtered.jar')
            ],
            'pgconf': [
                '%s_proguard.config' % V17_19_PREFIX,
                '%s_proguard_extra.config' % V17_19_PREFIX,
                '%s/proguardsettings/YouTubeRelease_proguard.config' %
                utils.THIRD_PARTY, utils.IGNORE_WARNINGS_RULES
            ],
            'min-api':
                ANDROID_M_API,
            'system-properties': [
                # TODO(b/235169948): Reenable -checkenumunboxed.
                # '-Dcom.android.tools.r8.experimental.enablecheckenumunboxed=1',
                '-Dcom.android.tools.r8.experimental.enableconvertchecknotnull=1'
            ],
        },
    },
}


def GetLatestVersion():
    return LATEST_VERSION


def GetName():
    return 'youtube'


def GetMemoryData(version):
    assert version == '16.20'
    return {
        'find-xmx-min': 3150,
        'find-xmx-max': 3300,
        'find-xmx-range': 64,
        'oom-threshold': 3100,
        # TODO(b/143431825): Youtube can OOM randomly in memory configurations
        #  that should work.
        'skip-find-xmx-max': True,
    }
