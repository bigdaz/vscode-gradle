import * as vscode from 'vscode';
import * as path from 'path';
import * as fs from 'fs';
import * as os from 'os';
import * as cp from 'child_process';

import { getIsAutoDetectionEnabled } from './config';

type GradleTask = {
  path: string;
  description: string;
  name: string;
  group: string;
  project: string;
};

type GradleProject = {
  name: string;
  path: string;
  parent: string;
  tasks: GradleTask[];
  buildFile: string;
};

export interface GradleTaskDefinition extends vscode.TaskDefinition {
  script: string;
  description: string;
  group: string;
  project: string;
  rootProject: string;
  buildFile: string;
}

let autoDetectOverride = false;
let cachedTasks: Promise<vscode.Task[]> | undefined;
let gradleTasksProcess: cp.ChildProcessWithoutNullStreams | undefined;

export function enableTaskDetection(): void {
  autoDetectOverride = true;
}

export function killRefreshProcess(): void {
  if (gradleTasksProcess) {
    gradleTasksProcess.kill();
  }
}

async function isGradleProject(
  folder: vscode.WorkspaceFolder
): Promise<boolean> {
  const defaultBuildFileGlob = '{*.gradle,*.gradle.kts}';
  const relativePattern = new vscode.RelativePattern(
    folder,
    defaultBuildFileGlob
  );
  const files = await vscode.workspace.findFiles(relativePattern);
  return files.length > 0;
}

export class GradleTaskProvider implements vscode.TaskProvider {
  constructor(
    readonly statusBarItem: vscode.StatusBarItem,
    readonly outputChannel: vscode.OutputChannel,
    readonly context: vscode.ExtensionContext
  ) {}

  async provideTasks(): Promise<vscode.Task[] | undefined> {
    try {
      return await this.provideGradleTasks();
    } catch (e) {
      const message = `Error providing gradle tasks: ${e.message}`;
      console.error(message);
      this.outputChannel.appendLine(message);
    }
  }

  // TODO
  public async resolveTask(/*
    _task: vscode.Task
  */): Promise<
    vscode.Task | undefined
  > {
    return undefined;
  }

  private provideGradleTasks(): Promise<vscode.Task[]> {
    if (!cachedTasks) {
      cachedTasks = this.detectGradleTasks();
    }
    return cachedTasks;
  }

  private async detectGradleTasks(): Promise<vscode.Task[]> {
    const emptyTasks: vscode.Task[] = [];
    const allTasks: vscode.Task[] = [];
    const folders = vscode.workspace.workspaceFolders;
    if (!folders) {
      return emptyTasks;
    }
    try {
      for (const folder of folders) {
        if (autoDetectOverride || getIsAutoDetectionEnabled(folder)) {
          if (await isGradleProject(folder)) {
            const tasks = await this.provideGradleTasksForFolder(folder);
            allTasks.push(...tasks);
          }
        }
      }
      return allTasks;
    } catch (error) {
      return Promise.reject(error);
    }
  }

  private async provideGradleTasksForFolder(
    folder: vscode.WorkspaceFolder
  ): Promise<vscode.Task[]> {
    const emptyTasks: vscode.Task[] = [];

    const command = await getGradleWrapperCommandFromPath(folder.uri.fsPath);
    if (!command) {
      return emptyTasks;
    }
    const gradleProjects:
      | GradleProject[]
      | undefined = await this.getGradleProjects(folder);
    if (!gradleProjects || !gradleProjects.length) {
      return emptyTasks;
    }

    const rootProject: GradleProject | undefined = gradleProjects.find(
      project => !project.parent
    );
    if (!rootProject) {
      return emptyTasks;
    }

    return gradleProjects.reduce(
      (allTasks: vscode.Task[], gradleProject: GradleProject) => {
        return allTasks.concat(
          gradleProject.tasks.map(gradleTask =>
            createTaskFromGradleTask(
              gradleTask,
              folder!,
              command,
              rootProject.name,
              vscode.Uri.file(gradleProject.buildFile)
            )
          )
        );
      },
      []
    );
  }

  private async getProjectsFromGradle(
    folder: vscode.WorkspaceFolder
  ): Promise<string> {
    this.statusBarItem.text = '$(sync~spin) Refreshing gradle tasks';
    this.statusBarItem.show();
    const tempDir = await fs.promises.mkdtemp(
      path.join(os.tmpdir(), 'vscode-gradle-')
    );
    const tempFile = path.join(tempDir, 'tasks.json');
    const command = getTasksScriptCommand();
    const cwd = this.context.asAbsolutePath('lib');
    const args = [folder.uri.fsPath, tempFile];
    return executeGradleTasksCommand(command, args, { cwd }, this.outputChannel)
      .then(() => fs.promises.readFile(tempFile, 'utf8'))
      .finally(async () => {
        this.statusBarItem.hide();
        if (await exists(tempFile)) {
          await fs.promises.unlink(tempFile);
        }
        await fs.promises.rmdir(tempDir);
      });
  }

  private async getGradleProjects(
    folder: vscode.WorkspaceFolder
  ): Promise<GradleProject[] | undefined> {
    const stdout = await this.getProjectsFromGradle(folder);
    return parseGradleProjects(stdout);
  }
}

export function invalidateTasksCache(): void {
  cachedTasks = undefined;
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
export function isWorkspaceFolder(value: any): value is vscode.WorkspaceFolder {
  return value && typeof value !== 'number';
}

export async function getGradleWrapperCommandFromPath(
  fsPath: string
): Promise<string> {
  const platform = process.platform;
  if (
    platform === 'win32' &&
    (await exists(path.join(fsPath, 'gradlew.bat')))
  ) {
    return '.\\gradlew.bat';
  } else if (
    (platform === 'linux' || platform === 'darwin') &&
    (await exists(path.join(fsPath, 'gradlew')))
  ) {
    return './gradlew';
  } else {
    throw new Error('Gradle wrapper executable not found');
  }
}

export function getTasksScriptCommand(): string {
  const platform = process.platform;
  if (platform === 'win32') {
    return '.\\gradle-tasks.bat';
  } else if (platform === 'linux' || platform === 'darwin') {
    return './gradle-tasks';
  } else {
    throw new Error('Unsupported platform');
  }
}

export function createTaskFromDefinition(
  definition: vscode.TaskDefinition,
  folder: vscode.WorkspaceFolder,
  cmd: string,
  args: string[] = []
): vscode.Task {
  const crossShellCmd = `"${cmd}"`;
  const cwd = folder.uri.fsPath;
  const allArgs = [definition.script, ...args];
  const task = new vscode.Task(
    definition,
    folder,
    definition.script,
    'gradle',
    new vscode.ShellExecution(crossShellCmd, allArgs, { cwd }),
    ['$gradle']
  );
  task.presentationOptions = {
    clear: true,
    showReuseMessage: false,
    focus: true
  };
  return task;
}

export function createTaskFromGradleTask(
  gradleTask: GradleTask,
  folder: vscode.WorkspaceFolder,
  command: string,
  rootProject: string,
  buildFile: vscode.Uri,
  shellArgs: string[] = []
): vscode.Task {
  const friendlyTaskName = gradleTask.path.replace(/^:/, '');
  const definition: GradleTaskDefinition = {
    type: 'gradle',
    script: friendlyTaskName,
    description: gradleTask.description,
    group: (gradleTask.group || 'other').toLowerCase(),
    project: gradleTask.project,
    buildFile: buildFile.fsPath,
    rootProject
  };
  return createTaskFromDefinition(definition, folder, command, shellArgs);
}

export async function cloneTask(
  task: vscode.Task,
  args: string[] = []
): Promise<vscode.Task | undefined> {
  const folder = task.scope as vscode.WorkspaceFolder;
  const command = await getGradleWrapperCommandFromPath(folder.uri.fsPath);
  if (!command) {
    return undefined;
  }
  return createTaskFromDefinition(task.definition, folder, command, args);
}

export async function hasGradleProject(): Promise<boolean> {
  const folders = vscode.workspace.workspaceFolders;
  if (!folders) {
    return false;
  }
  for (const folder of folders) {
    if (folder.uri.scheme !== 'file') {
      continue;
    }
    if (await isGradleProject(folder)) {
      return true;
    }
  }
  return false;
}

async function exists(file: string): Promise<boolean> {
  return new Promise<boolean>(resolve => {
    fs.exists(file, value => {
      resolve(value);
    });
  });
}

export function parseGradleProjects(buffer: Buffer | string): GradleProject[] {
  const input = buffer.toString();
  try {
    return JSON.parse(input);
  } catch (e) {
    throw new Error(`${e.message} - input: ${input}`);
  }
}

function debugCommand(
  command: string,
  args: ReadonlyArray<string>,
  outputChannel: vscode.OutputChannel
): void {
  const message = `Executing: ${command} ${args.join(' ')}`;
  outputChannel.appendLine(message);
}

export function executeGradleTasksCommand(
  command: string,
  args: ReadonlyArray<string> = [],
  options: cp.SpawnOptionsWithoutStdio = {},
  outputChannel: vscode.OutputChannel
): Promise<string> {
  debugCommand(command, args, outputChannel);
  return new Promise((resolve, reject) => {
    gradleTasksProcess = cp.spawn(command, args, options);
    gradleTasksProcess.stdout.on('data', (buffer: Buffer) =>
      outputChannel.append(buffer.toString())
    );
    gradleTasksProcess.stderr.on('data', (buffer: Buffer) =>
      outputChannel.append(buffer.toString())
    );
    gradleTasksProcess.on('error', reject);
    gradleTasksProcess.on('exit', (code: number) => {
      if (code === 0) {
        resolve();
      } else {
        reject(new Error(`Process exited with code ${code}`));
      }
    });
  });
}
