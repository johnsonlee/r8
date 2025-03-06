#!/usr/bin/env python3
# Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import argparse
import os
import shutil
import sys
import utils


def parse_options():
    parser = argparse.ArgumentParser(description='Release r8')
    parser.add_argument('--sdk-home',
                        '--sdk_home',
                        metavar=('<SDK_HOME>'),
                        help='SDK_HOME to use for finding SDK')
    parser.add_argument('--sdk-name',
                        '--sdk_name',
                        required=True,
                        metavar=('<name>'),
                        help='Name of the SDK, either API level or code name')
    parser.add_argument('--api-level',
                        '--api_level',
                        metavar=('<level>'),
                        help='API level to add this as in third_party')
    return parser.parse_args()


def remove(path):
    try:
        os.remove(path)
    except FileNotFoundError:
        pass


def generate_readme_google(destination, sdk_name):
    with open(os.path.join(destination, 'README.google'), 'w') as f:
        f.write('Name: Android SDK excerpts\n')
        f.write('URL: https://developer.android.com/tools/sdkmanager\n')
        f.write('Version: API version %s\n' % sdk_name)
        f.write('Revision: N/A\n')
        f.write('License: Apache License Version 2.0\n')
        f.write('\n')
        f.write('Description:\n')
        f.write('This is excerpts from an Android SDK including android.jar.\n')


def main():
    args = parse_options()

    if not args.sdk_home:
        args.sdk_home = os.environ.get('SDK_HOME')
        if not args.sdk_home:
            print('No SDK_HOME specified')
            sys.exit(1)
        else:
            print('Using SDK_HOME: %s' % args.sdk_home)

    source = os.path.join(args.sdk_home, 'platforms',
                          'android-%s' % args.sdk_name)
    if not os.path.exists(source):
        print('Path %s does not exist' % source)
        sys.exit(1)

    api_level = -1
    try:
        api_level = int(args.api_level if args.api_level else args.sdk_name)
    except:
        print('API level "%s" must be an integer'
            % (args.api_level if args.api_level else args.sdk_name))
        sys.exit(1)

    destination = utils.get_android_jar_dir(api_level)

    # Remove existing if present.
    shutil.rmtree(destination, ignore_errors=True)
    remove(destination + 'tar.gz')
    remove(destination + 'tar.gz.sha1')

    # Copy the files of interest.
    destination_optional_path = os.path.join(destination, 'optional')
    os.makedirs(os.path.join(destination_optional_path))
    shutil.copyfile(os.path.join(source, 'android.jar'),
                    os.path.join(destination, 'android.jar'))
    shutil.copyfile(os.path.join(source, 'data', 'api-versions.xml'),
                    os.path.join(destination, 'api-versions.xml'))
    source_optional_path = os.path.join(source, 'optional')
    for root, dirnames, filenames in os.walk(source_optional_path):
        for filename in filenames:
            if filename.endswith('.jar') or filename == 'optional.json':
                shutil.copyfile(
                    os.path.join(source_optional_path, filename),
                    os.path.join(destination_optional_path, filename))
    generate_readme_google(destination, args.sdk_name)

    print('If you want to upload this run:')
    print('  (cd %s; upload_to_google_storage.py -a --bucket r8-deps %s)' %
          (os.path.dirname(destination), os.path.basename(destination)))
    print('Update d8_r8/commonBuildSrc/src/main/kotlin/DependenciesPlugin.kt'
          ' if this is a new dependency.')
    print('Run main method in AndroidApiHashingDatabaseBuilderGeneratorTest'
        ' to generate the API database.')

if __name__ == '__main__':
    sys.exit(main())
