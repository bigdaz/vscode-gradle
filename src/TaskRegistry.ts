import {
  window,
  workspace,
  Disposable,
  OutputChannel,
  QuickPickItem
} from 'vscode';

import Gradle from './Gradle';
import ProcessRegistry from './ProcessRegistry';

export interface Task extends QuickPickItem {}

const TASK_REGEX: RegExp = /$\s*([a-z0-9]+)\s-\s(.*)$/gim;
const tasks: Set<Task> = new Set();

function add(task: Task): void {
  tasks.add(task);
}

function addAll(tasks: Task[]): void {
  tasks.forEach(add);
}

function clear(): void {
  tasks.clear();
}

function getTasks(): Task[] {
  return Array.from(tasks);
}

function getTasksFromGradle(): Thenable<Task[]> {
  const cmd = `${Gradle.getCommand()} --console plain tasks ${Gradle.getTasksArgs()}`;
  const cwd = workspace.rootPath;
  return ProcessRegistry.create(cmd, { cwd }).then(stdout => {
    let match: RegExpExecArray;
    const tasks: Task[] = [];
    while ((match = TASK_REGEX.exec(stdout)) !== null) {
      tasks.push({
        label: match[1],
        description: match[2]
      });
    }
    return tasks.sort((a, b) => a.label.localeCompare(b.label));
  });
}

function refresh(): Thenable<void> {
  const statusbar: Disposable = window.setStatusBarMessage(
    'Refreshing gradle tasks'
  );
  return getTasksFromGradle().then(
    gradleTasks => {
      clear();
      addAll(gradleTasks);
      statusbar.dispose();
    },
    err => {
      statusbar.dispose();
      return Promise.reject(err);
    }
  );
}

export default { refresh, clear, getTasks };
