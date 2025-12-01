#!/usr/bin/env python3
# Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import argparse
import os
import shutil
import sys

sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import adb
import apk_masseur
import compiledump
import utils
import jdk


class AttrDict(dict):

    def __getattr__(self, name):
        return self.get(name, None)


def instrument_dex(options, temp_dir, instrumented_dex):
    with utils.TempDir() as compiledump_temp_dir:
        utils.RunCmd(
            ['unzip', options.apk, '*.dex', '-d', compiledump_temp_dir])
        with utils.ChangedWorkingDirectory(compiledump_temp_dir):
            dex_jar = os.path.join(compiledump_temp_dir, 'dex.jar')
            utils.RunCmd('zip %s *.dex' % dex_jar, shell=True)
        args_for_assistant = AttrDict(
            vars(
                argparse.Namespace(
                    dump=options.dump,
                    program_jar=dex_jar,
                    compiler='assistant',
                    disable_assertions=options.disable_assertions,
                    debug_agent=options.debug_agent,
                    version='main',
                    no_build=options.no_build,
                    enable_missing_library_api_modeling=False,
                    java_opts=[],
                )))
        otherargs = ['--output', instrumented_dex]
        if options.json_output:
            otherargs.append('--reflective-usage-json-output')
        compile_result = compiledump.run1(compiledump_temp_dir,
                                          args_for_assistant, otherargs)
        if compile_result != 0:
            raise Exception('Failed to run R8 assistant')


def export_keep_info(options, temp_dir):
    with utils.TempDir() as compiledump_temp_dir:
        otherargs = [
            '-Dcom.android.tools.r8.exportInitialKeepInfoCollection=%s' %
            os.path.join(temp_dir, 'keepinfo')
        ]
        args_for_r8 = AttrDict(
            vars(
                argparse.Namespace(
                    dump=options.dump,
                    compiler='r8',
                    disable_assertions=options.disable_assertions,
                    version='main',
                    nolib=True,
                    no_build=options.no_build,
                    java_opts=[],
                )))
        compile_result = compiledump.run1(compiledump_temp_dir, args_for_r8,
                                          otherargs)
        if compile_result != 0:
            raise Exception('Failed to run R8 to export keep info collection')


def build_instrumented_apk(options, temp_dir, instrumented_dex):
    # Create new app apk with instrumented dex.
    instrumented_app_apk = os.path.join(temp_dir, "app.apk")
    apk_masseur.masseur(options.apk,
                        dex=instrumented_dex,
                        out=instrumented_app_apk,
                        keystore=options.keystore)
    return instrumented_app_apk


def sign_test_apk(options, temp_dir):
    # Sign test apk with same key as the instrumented dex apk.
    original_test_apk = options.apk_test
    test_apk_destination = os.path.join(temp_dir, "test.apk")
    apk_masseur.masseur(original_test_apk,
                        out=test_apk_destination,
                        keystore=options.keystore)
    return test_apk_destination


def run_tests(options, temp_dir, instrumented_app_apk, test_apk_destination):

    def on_completion_callback():
        if options.json_output:
            adb.pull_file('/data/user/0/%s/cache/log.txt' % options.id,
                          os.path.join(temp_dir, 'log.txt'), options.device)

    adb.run_instrumented(options.id,
                         options.id_test,
                         options.device,
                         instrumented_app_apk,
                         test_apk_destination,
                         quiet=False,
                         enable_logging=True,
                         test_runner=None,
                         on_completion_callback=on_completion_callback)


def check_reflective_operations(options, temp_dir):
    if options.json_output:
        log_file = os.path.join(temp_dir, 'log.txt')
        keep_info_dir = os.path.join(temp_dir, 'keepinfo')
        if os.path.exists(log_file) and os.path.exists(keep_info_dir):
            cmd = [
                jdk.GetJavaExecutable(), '-cp', utils.R8_JAR,
                'com.android.tools.r8.assistant.postprocessing.CheckReflectiveOperations',
                log_file, keep_info_dir
            ]
            utils.RunCmd(cmd)
        else:
            raise Exception(
                "Missing log file or keep info directory for reflective operations check."
            )


def run_r8_assistant(options, temp_dir):
    if not options.skip_compilations:
        instrumented_dex = os.path.join(temp_dir, 'out.jar')
        instrument_dex(options, temp_dir, instrumented_dex)
        export_keep_info(options, temp_dir)
        instrumented_app_apk = build_instrumented_apk(options, temp_dir,
                                                      instrumented_dex)
        test_apk_destination = sign_test_apk(options, temp_dir)
        run_tests(options, temp_dir, instrumented_app_apk, test_apk_destination)
    check_reflective_operations(options, temp_dir)


def parse_options(argv):
    result = argparse.ArgumentParser(description='Run/compile dump artifacts.')
    result.add_argument('--apk', help='Path to apk for app')
    result.add_argument('--apk-test', help='Path to apk for androidTest')
    result.add_argument('--dump', help='Path to compiler dump')
    result.add_argument('--id', help='The id of the app')
    result.add_argument('--id-test', help='The id of the test')
    result.add_argument('--device', help='The device to run on')
    result.add_argument('--skip-compilations',
                        help='Skip compilations (default disabled)',
                        default=False,
                        action='store_true'),
    result.add_argument('--json-output',
                        help='Use json for reflective use (default disabled)',
                        default=False,
                        action='store_true')
    result.add_argument('--debug-agent',
                        help='Enable Java debug agent and suspend compilation ',
                        default=False,
                        action='store_true')
    result.add_argument(
        '--disable-assertions',
        '--disable_assertions',
        '-da',
        help='Disable Java assertions when running the compiler',
        default=False,
        action='store_true')
    result.add_argument('--keystore',
                        help='Path to app.keystore',
                        default=os.path.join(utils.TOOLS_DIR, 'debug.keystore'))
    result.add_argument('--no-build',
                        '--no_build',
                        help='Run without building first (only when using ToT)',
                        default=False,
                        action='store_true')
    result.add_argument('--temp',
                        help='A directory to use for temporaries and outputs.')
    return result.parse_known_args(argv)


def main(argv):
    options, _ = parse_options(argv)

    with utils.TempDir() as temp_dir:
        if options.temp:
            temp_dir = options.temp
            os.makedirs(temp_dir, exist_ok=True)
        run_r8_assistant(options, temp_dir)


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
