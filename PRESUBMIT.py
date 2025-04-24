# Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

from os import path
import datetime
from subprocess import check_output, Popen, PIPE, STDOUT
import inspect
import os
import sys
# Add both current path to allow us to package import utils and the tools
# dir to allow transitive (for utils) dependendies to be loaded.
sys.path.append(path.dirname(inspect.getfile(lambda: None)))
sys.path.append(os.path.join(
    path.dirname(inspect.getfile(lambda: None)), 'tools'))
from tools.utils import EnsureDepFromGoogleCloudStorage
from tools.jdk import GetJavaExecutable


KOTLIN_FMT_JAR = path.join(
  'third_party',
  'google',
  'google-kotlin-format',
  '0.54',
  'ktfmt-0.54-jar-with-dependencies.jar')

KOTLIN_FMT_SHA1 = path.join(
    'third_party', 'google', 'google-kotlin-format', '0.54.tar.gz.sha1')
KOTLIN_FMT_TGZ = path.join(
    'third_party', 'google', 'google-kotlin-format', '0.54.tar.gz.sha1')
KOTLIN_FMT_IGNORE = {
  'src/test/java/com/android/tools/r8/kotlin/metadata/inline_class_fun_descriptor_classes_app/main.kt'
}
KOTLIN_FMT_BATCH_SIZE = 100

FMT_CMD = path.join(
    'third_party',
    'google',
    'google-java-format',
    '1.24.0',
    'google-java-format-1.24.0',
    'scripts',
    'google-java-format-diff.py')

FMT_CMD_JDK17 = path.join('tools','google-java-format-diff.py')
FMT_SHA1 = path.join(
    'third_party', 'google', 'google-java-format', '1.24.0.tar.gz.sha1')
FMT_TGZ = path.join(
    'third_party', 'google', 'google-java-format', '1.24.0.tar.gz')

def CheckDoNotMerge(input_api, output_api):
  for l in input_api.change.FullDescriptionText().splitlines():
    if l.lower().startswith('do not merge'):
      msg = 'Your cl contains: \'Do not merge\' - this will break WIP bots'
      return [output_api.PresubmitPromptWarning(msg, [])]
  return []

def CheckFormatting(input_api, output_api, branch):
  seen_kotlin_error = False
  seen_java_error = False
  pending_kotlin_files = []
  EnsureDepFromGoogleCloudStorage(
    KOTLIN_FMT_JAR, KOTLIN_FMT_TGZ, KOTLIN_FMT_SHA1, 'google-kotlin-format')
  EnsureDepFromGoogleCloudStorage(
    FMT_CMD, FMT_TGZ, FMT_SHA1, 'google-java-format')
  results = []
  for f in input_api.AffectedFiles():
    path = f.LocalPath()
    if not path.endswith('.java') and not path.endswith('.kt'):
      continue
    if path.endswith('.kt'):
      if path in KOTLIN_FMT_IGNORE:
        continue
      pending_kotlin_files.append(path)
      if len(pending_kotlin_files) == KOTLIN_FMT_BATCH_SIZE:
        seen_kotlin_error = (CheckKotlinFormatting(pending_kotlin_files, output_api, results)
                             or seen_kotlin_error)
        pending_kotlin_files = []
    else:
      seen_java_error = (CheckJavaFormatting(path, branch, output_api, results)
                         or seen_java_error)
  # Check remaining Kotlin files if any.
  if len(pending_kotlin_files) > 0:
    seen_kotlin_error = (CheckKotlinFormatting(pending_kotlin_files, output_api, results)
                         or seen_kotlin_error)
  # Provide the reformatting commands if needed.
  if seen_kotlin_error:
    results.append(output_api.PresubmitError(KotlinFormatPresubmitMessage()))
  if seen_java_error:
    results.append(output_api.PresubmitError(JavaFormatPresubMessage()))

  # Comment this out to easily presumbit changes
  # results.append(output_api.PresubmitError("TESTING"))
  return results


def CheckKotlinFormatting(paths, output_api, results):
  paths_to_format = {
      '--kotlinlang-style': [path for path in paths if path.startswith('src/keepanno/')],
      '--google-style': [path for path in paths if not path.startswith('src/keepanno/')]
  }
  needs_formatting_count = 0
  for format in ['--kotlinlang-style', '--google-style']:
    cmd = [GetJavaExecutable(), '-jar', KOTLIN_FMT_JAR, format, '-n']
    to_format = paths_to_format[format]
    if len(to_format) > 0:
      cmd.extend(to_format)
      result = check_output(cmd)
      if len(result) > 0:
        with_format_error = result.splitlines()
        for path in with_format_error:
          results.append(
            output_api.PresubmitError(
              "File {path} needs formatting".format(path=path.decode('utf-8'))))
      needs_formatting_count += len(result)
  return needs_formatting_count > 0


def KotlinFormatPresubmitMessage():
  return """Please fix the Kotlin formatting by running:

  git diff $(git cl upstream) --name-only "*.kt" | grep -v "^src/keepanno/" | xargs {java} -jar {fmt_jar} --google-style
  git diff $(git cl upstream) --name-only "*.kt" | grep "^src/keepanno/" | xargs {java} -jar {fmt_jar} --kotlinlang-style

or fix formatting, commit and upload:

  git diff $(git cl upstream) --name-only "*.kt" | grep -v "^src/keepanno/" | xargs {java} -jar {fmt_jar} --google-style && git commit -a --amend --no-edit && git cl upload
  git diff $(git cl upstream) --name-only "*.kt" | grep "^src/keepanno/" | xargs {java} -jar {fmt_jar} --kotlinlang-style && git commit -a --amend --no-edit && git cl upload

or bypass the checks with:

  git cl upload --bypass-hooks
    """.format(java=GetJavaExecutable(), fmt_jar=KOTLIN_FMT_JAR)


def CheckJavaFormatting(path, branch, output_api, results):
  diff = check_output(
      ['git', 'diff', '--no-prefix', '-U0', branch, '--', path])

  proc = Popen(FMT_CMD, stdin=PIPE, stdout=PIPE, stderr=STDOUT)
  (stdout, stderr) = proc.communicate(input=diff)
  if len(stdout) > 0:
    results.append(output_api.PresubmitError(stdout.decode('utf-8')))
  return len(stdout) > 0


def JavaFormatPresubMessage():
  return """Please fix the Java formatting by running:

  git diff -U0 $(git cl upstream) | %s -p1 -i

or fix formatting, commit and upload:

  git diff -U0 $(git cl upstream) | %s -p1 -i && git commit -a --amend --no-edit && git cl upload

or bypass the checks with:

  git cl upload --bypass-hooks

If formatting fails with 'No enum constant javax.lang.model.element.Modifier.SEALED' try

  git diff -U0 $(git cl upstream) | %s %s %s -p1 -i && git commit -a --amend --no-edit && git cl upload
  """ % (
    FMT_CMD,
    FMT_CMD,
    FMT_CMD_JDK17,
    '--google-java-format-jar',
    'third_party/google/google-java-format/1.24.0/google-java-format-1.24.0-all-deps.jar')


def CheckDeterministicDebuggingChanged(input_api, output_api, branch):
  for f in input_api.AffectedFiles():
    path = f.LocalPath()
    if not path.endswith('InternalOptions.java'):
      continue
    diff = check_output(
        ['git', 'diff', '--no-prefix', '-U0', branch, '--', path]).decode('utf-8')
    if 'DETERMINISTIC_DEBUGGING' in diff:
      return [output_api.PresubmitError(diff)]
  return []

def CheckForAddedDisassemble(input_api, output_api):
  results = []
  for (file, line_nr, line) in input_api.RightHandSideLines():
    if file.LocalPath().endswith('.java') and '.disassemble()' in line:
      results.append(
          output_api.PresubmitError(
              'Test call to disassemble\n%s:%s %s' % (file.LocalPath(), line_nr, line)))
  return results

def CheckForAddedPartialDebug(input_api, output_api):
  results = []
  for (file, line_nr, line) in input_api.RightHandSideLines():
    if not file.LocalPath().endswith('.java'):
      continue
    if '.enablePrintPartialCompilationPartitioning(' in line:
      results.append(
          output_api.PresubmitError(
              'Test call to enablePrintPartialCompilationPartitioning\n%s:%s %s' % (
                file.LocalPath(), line_nr, line)))
    if '.setPartialCompilationSeed(' in line:
      results.append(
          output_api.PresubmitError(
              'Test call to setPartialCompilationSeed\n%s:%s %s' % (
                file.LocalPath(), line_nr, line)))
  return results

def CheckForCopyRight(input_api, output_api, branch):
  results = []
  for f in input_api.AffectedSourceFiles(None):
    # Check if it is a new file.
    if f.OldContents():
      continue
    contents = f.NewContents()
    if (not contents) or (len(contents) == 0):
      continue
    if not CopyRightInContents(f, contents):
      results.append(
          output_api.PresubmitError('Could not find correctly formatted '
                                    'copyright in file: %s' % f))
  return results

def CopyRightInContents(f, contents):
  expected = '//'
  if f.LocalPath().endswith('.py') or f.LocalPath().endswith('.sh'):
    expected = '#'
  expected = expected + ' Copyright (c) ' + str(datetime.datetime.now().year)
  for content_line in contents:
    if expected in content_line:
      return True
  return False

def CheckChange(input_api, output_api):
  branch = (
      check_output(['git', 'cl', 'upstream'])
          .decode('utf-8')
          .strip()
          .replace('refs/heads/', ''))
  results = []
  results.extend(CheckDoNotMerge(input_api, output_api))
  results.extend(CheckFormatting(input_api, output_api, branch))
  results.extend(
      CheckDeterministicDebuggingChanged(input_api, output_api, branch))
  results.extend(CheckForAddedDisassemble(input_api, output_api))
  results.extend(CheckForAddedPartialDebug(input_api, output_api))
  results.extend(CheckForCopyRight(input_api, output_api, branch))
  return results

def CheckChangeOnCommit(input_api, output_api):
  return CheckChange(input_api, output_api)

def CheckChangeOnUpload(input_api, output_api):
  return CheckChange(input_api, output_api)
