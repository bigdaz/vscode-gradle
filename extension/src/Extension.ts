import * as vscode from 'vscode';
import * as path from 'path';
import * as net from 'net';
import { logger, LogVerbosity, Logger } from './logger';
import { Api } from './api';
import { GradleClient } from './client';
import { GradleServer } from './server';
import { Icons } from './icons';
import {
  GradleDaemonsTreeDataProvider,
  PinnedTasksTreeDataProvider,
  RecentTasksTreeDataProvider,
  GradleTasksTreeDataProvider,
} from './views';
import {
  PinnedTasksStore,
  RecentTasksStore,
  TaskTerminalsStore,
  RootProjectsStore,
} from './stores';
import {
  GradleTaskProvider,
  GradleTaskManager,
  GradleTaskDefinition,
} from './tasks';
import {
  GRADLE_TASKS_VIEW,
  PINNED_TASKS_VIEW,
  GRADLE_DAEMONS_VIEW,
  RECENT_TASKS_VIEW,
} from './views/constants';
import { focusTaskInGradleTasksTree } from './views/viewUtil';
import { COMMAND_RENDER_TASK, COMMAND_REFRESH } from './commands';
import { Commands } from './commands/Commands';
import {
  getConfigIsDebugEnabled,
  getConfigFocusTaskInExplorer,
} from './util/config';
import { FileWatcher } from './util/FileWatcher';
import { DependencyTreeItem } from './views/gradleTasks/DependencyTreeItem';
import { GRADLE_DEPENDENCY_REVEAL } from './views/gradleTasks/DependencyUtils';
import { GradleDependencyProvider } from './dependencies/GradleDependencyProvider';
import { getSpecificVersionStatus } from './views/gradleDaemons/util';
import { LanguageClientOptions } from 'vscode-languageclient';
import { LanguageClient, StreamInfo } from 'vscode-languageclient/node';
import { OutputInfoCollector } from './OutputInfoCollector';

export class Extension {
  private readonly client: GradleClient;
  private readonly server: GradleServer;
  private readonly pinnedTasksStore: PinnedTasksStore;
  private readonly recentTasksStore: RecentTasksStore;
  private readonly taskTerminalsStore: TaskTerminalsStore;
  private readonly rootProjectsStore: RootProjectsStore;
  private readonly gradleTaskProvider: GradleTaskProvider;
  private readonly gradleDependencyProvider: GradleDependencyProvider;
  private readonly taskProvider: vscode.Disposable;
  private readonly gradleTaskManager: GradleTaskManager;
  private readonly icons: Icons;
  private readonly buildFileWatcher: FileWatcher;
  private readonly gradleWrapperWatcher: FileWatcher;
  private readonly gradleDaemonsTreeView: vscode.TreeView<vscode.TreeItem>;
  private readonly gradleTasksTreeView: vscode.TreeView<vscode.TreeItem>;
  private readonly gradleDaemonsTreeDataProvider: GradleDaemonsTreeDataProvider;
  private readonly pinnedTasksTreeDataProvider: PinnedTasksTreeDataProvider;
  private readonly pinnedTasksTreeView: vscode.TreeView<vscode.TreeItem>;
  private readonly recentTasksTreeDataProvider: RecentTasksTreeDataProvider;
  private readonly recentTasksTreeView: vscode.TreeView<vscode.TreeItem>;
  private readonly gradleTasksTreeDataProvider: GradleTasksTreeDataProvider;
  private readonly api: Api;
  private readonly commands: Commands;
  private readonly _onDidTerminalOpen: vscode.EventEmitter<vscode.Terminal> = new vscode.EventEmitter<vscode.Terminal>();
  private readonly onDidTerminalOpen: vscode.Event<vscode.Terminal> = this
    ._onDidTerminalOpen.event;
  private recentTerminal: vscode.Terminal | undefined;

  public constructor(private readonly context: vscode.ExtensionContext) {
    const loggingChannel = vscode.window.createOutputChannel('Gradle Tasks');
    logger.setLoggingChannel(loggingChannel);

    const clientLogger = new Logger('grpc');
    clientLogger.setLoggingChannel(loggingChannel);

    const serverLogger = new Logger('gradle-server');
    serverLogger.setLoggingChannel(loggingChannel);

    if (getConfigIsDebugEnabled()) {
      Logger.setLogVerbosity(LogVerbosity.DEBUG);
    }

    const statusBarItem = vscode.window.createStatusBarItem();
    this.server = new GradleServer(
      { host: 'localhost' },
      context,
      serverLogger
    );
    this.client = new GradleClient(this.server, statusBarItem, clientLogger);
    this.pinnedTasksStore = new PinnedTasksStore(context);
    this.recentTasksStore = new RecentTasksStore();
    this.taskTerminalsStore = new TaskTerminalsStore();
    this.rootProjectsStore = new RootProjectsStore();
    this.gradleTaskProvider = new GradleTaskProvider(
      this.rootProjectsStore,
      this.client
    );
    this.gradleDependencyProvider = new GradleDependencyProvider(this.client);
    this.taskProvider = vscode.tasks.registerTaskProvider(
      'gradle',
      this.gradleTaskProvider
    );
    this.icons = new Icons(context);

    this.gradleTasksTreeDataProvider = new GradleTasksTreeDataProvider(
      this.context,
      this.rootProjectsStore,
      this.gradleTaskProvider,
      this.gradleDependencyProvider,
      this.icons
    );
    this.gradleTasksTreeView = vscode.window.createTreeView(GRADLE_TASKS_VIEW, {
      treeDataProvider: this.gradleTasksTreeDataProvider,
      showCollapseAll: true,
    });
    this.gradleDaemonsTreeDataProvider = new GradleDaemonsTreeDataProvider(
      this.context,
      this.rootProjectsStore,
      this.client
    );
    this.gradleDaemonsTreeView = vscode.window.createTreeView(
      GRADLE_DAEMONS_VIEW,
      {
        treeDataProvider: this.gradleDaemonsTreeDataProvider,
        showCollapseAll: false,
      }
    );
    this.pinnedTasksTreeDataProvider = new PinnedTasksTreeDataProvider(
      this.pinnedTasksStore,
      this.rootProjectsStore,
      this.gradleTaskProvider,
      this.icons,
      this.client
    );
    this.pinnedTasksTreeView = vscode.window.createTreeView(PINNED_TASKS_VIEW, {
      treeDataProvider: this.pinnedTasksTreeDataProvider,
      showCollapseAll: false,
    });

    this.recentTasksTreeDataProvider = new RecentTasksTreeDataProvider(
      this.recentTasksStore,
      this.taskTerminalsStore,
      this.rootProjectsStore,
      this.gradleTaskProvider,
      this.client,
      this.icons
    );
    this.recentTasksTreeView = vscode.window.createTreeView(RECENT_TASKS_VIEW, {
      treeDataProvider: this.recentTasksTreeDataProvider,
      showCollapseAll: false,
    });

    this.gradleTaskManager = new GradleTaskManager(context);
    this.buildFileWatcher = new FileWatcher('**/*.{gradle,gradle.kts}');
    this.gradleWrapperWatcher = new FileWatcher(
      '**/gradle/wrapper/gradle-wrapper.properties'
    );
    this.api = new Api(
      this.client,
      this.gradleTasksTreeDataProvider,
      this.gradleTaskProvider,
      this.icons
    );

    this.commands = new Commands(
      this.context,
      this.pinnedTasksStore,
      this.gradleTaskProvider,
      this.gradleDependencyProvider,
      this.gradleTasksTreeDataProvider,
      this.pinnedTasksTreeDataProvider,
      this.recentTasksTreeDataProvider,
      this.gradleDaemonsTreeDataProvider,
      this.client,
      this.rootProjectsStore,
      this.taskTerminalsStore,
      this.recentTasksStore,
      this.gradleTasksTreeView
    );

    this.storeSubscriptions();
    this.registerCommands();
    this.handleTaskEvents();
    this.handleWatchEvents();
    this.handleEditorEvents();

    vscode.commands.registerCommand(
      GRADLE_DEPENDENCY_REVEAL,
      async (item: DependencyTreeItem) => {
        const omittedTreeItem = item.getOmittedTreeItem();
        if (omittedTreeItem) {
          await this.gradleTasksTreeView.reveal(omittedTreeItem);
        }
      }
    );

    void this.activate();

    const displayName = 'Gradle';

    void vscode.window.withProgress(
      { location: vscode.ProgressLocation.Window },
      (progress) => {
        // eslint-disable-next-line @typescript-eslint/no-unused-vars
        return new Promise<void>(async (resolve, _reject) => {
          progress.report({
            message: 'Initializing Gradle language server...',
          });
          const clientOptions: LanguageClientOptions = {
            documentSelector: [{ scheme: 'file', language: 'gradle' }],
            synchronize: {
              configurationSection: 'gradle',
            },
            uriConverters: {
              code2Protocol: (value: vscode.Uri): string => {
                if (/^win32/.test(process.platform)) {
                  // drive letters on Windows are encoded with %3A instead of :
                  // but Java doesn't treat them the same
                  return value.toString().replace('%3A', ':');
                } else {
                  return value.toString();
                }
              },
              // this is just the default behavior, but we need to define both
              protocol2Code: (value): vscode.Uri => vscode.Uri.parse(value),
            },
            outputChannel: new OutputInfoCollector(displayName),
            outputChannelName: displayName,
          };
          let serverOptions;
          if (
            process.env.VSCODE_DEBUG_LANGUAGE_SERVER?.toLowerCase() === 'true'
          ) {
            // debug mode
            serverOptions = this.awaitServerConnection.bind(null, '6006');
          } else {
            const args = [
              '-jar',
              path.resolve(
                context.extensionPath,
                'lib',
                'gradle-language-server.jar'
              ),
            ];
            serverOptions = {
              command:
                'C:/Program Files/AdoptOpenJDK/jdk-11.0.11.9-hotspot/bin/java',
              args: args,
            };
          }
          const languageClient = new LanguageClient(
            'gradle',
            'Gradle Language Server CS',
            serverOptions,
            clientOptions
          );
          languageClient.onReady().then(resolve, (reason: any) => {
            resolve();
            void vscode.window.showErrorMessage(reason);
          });
          const disposable = languageClient.start();
          context.subscriptions.push(disposable);
        });
      }
    );
  }

  private async awaitServerConnection(port: string): Promise<StreamInfo> {
    const addr = parseInt(port);
    return new Promise((res, rej) => {
      const server = net.createServer((stream) => {
        server.close();
        res({ reader: stream, writer: stream });
      });
      server.on('error', rej);
      server.listen(addr, () => {
        server.removeListener('error', rej);
      });
      return server;
    });
  }

  private storeSubscriptions(): void {
    this.context.subscriptions.push(
      this.client,
      this.server,
      this.pinnedTasksStore,
      this.recentTasksStore,
      this.taskTerminalsStore,
      this.gradleTaskProvider,
      this.taskProvider,
      this.gradleTaskManager,
      this.buildFileWatcher,
      this.gradleWrapperWatcher,
      this.gradleDaemonsTreeView,
      this.gradleTasksTreeView,
      this.pinnedTasksTreeView,
      this.recentTasksTreeView
    );
  }

  private async activate(): Promise<void> {
    const activated = !!(await this.rootProjectsStore.getProjectRoots()).length;
    if (activated && !this.server.isReady()) {
      await this.server.start();
    }
    await vscode.commands.executeCommand(
      'setContext',
      'gradle:activated',
      activated
    );
  }

  private registerCommands(): void {
    this.commands.register();
  }

  private handleTaskTerminals(
    definition: GradleTaskDefinition,
    terminal: vscode.Terminal
  ): void {
    // Add this task terminal to the store
    const terminalTaskName = terminal.name.replace('Task - ', '');
    if (terminalTaskName === definition.script) {
      this.taskTerminalsStore.addEntry(terminalTaskName, terminal);
    }
    terminal.show();
  }

  private handleTaskEvents(): void {
    this.gradleTaskManager.onDidStartTask(async (task: vscode.Task) => {
      const definition = task.definition as GradleTaskDefinition;

      // This madness is due to `vscode.window.onDidOpenTerminal` being handled differently
      // in different vscode versions.
      if (this.recentTerminal) {
        this.handleTaskTerminals(definition, this.recentTerminal);
        this.recentTerminal = undefined;
      } else {
        const disposable = this.onDidTerminalOpen(
          (terminal: vscode.Terminal) => {
            disposable.dispose();
            this.handleTaskTerminals(definition, terminal);
            this.recentTerminal = undefined;
          }
        );
      }

      if (this.gradleTasksTreeView.visible && getConfigFocusTaskInExplorer()) {
        await focusTaskInGradleTasksTree(task, this.gradleTasksTreeView);
      }
      this.recentTasksStore.addEntry(definition.id, definition.args);
      await vscode.commands.executeCommand(COMMAND_RENDER_TASK, task);
    });
    this.gradleTaskManager.onDidEndTask(async (task: vscode.Task) => {
      await vscode.commands.executeCommand(COMMAND_RENDER_TASK, task);
    });
  }

  private handleWatchEvents(): void {
    this.buildFileWatcher.onDidChange(async (uri: vscode.Uri) => {
      logger.info('Build file changed:', uri.fsPath);
      await this.refresh();
    });
    this.gradleWrapperWatcher.onDidChange(async (uri: vscode.Uri) => {
      logger.info('Gradle wrapper properties changed:', uri.fsPath);
      await this.restartServer();
    });
  }

  private async restartServer(): Promise<void> {
    if (this.server.isReady()) {
      await this.client.cancelBuilds();
      await this.server.restart();
    }
  }

  private refresh(): Thenable<void> {
    return vscode.commands.executeCommand(COMMAND_REFRESH);
  }

  private handleEditorEvents(): void {
    this.context.subscriptions.push(
      vscode.workspace.onDidChangeConfiguration(
        async (event: vscode.ConfigurationChangeEvent) => {
          if (
            event.affectsConfiguration('java.home') ||
            event.affectsConfiguration('java.import.gradle.java.home')
          ) {
            await this.restartServer();
          } else if (
            event.affectsConfiguration('gradle.javaDebug') ||
            event.affectsConfiguration('gradle.nestedProjects')
          ) {
            this.rootProjectsStore.clear();
            await this.refresh();
            await this.activate();
          } else if (event.affectsConfiguration('gradle.reuseTerminals')) {
            await this.refresh();
          } else if (event.affectsConfiguration('gradle.debug')) {
            const debug = getConfigIsDebugEnabled();
            Logger.setLogVerbosity(
              debug ? LogVerbosity.DEBUG : LogVerbosity.INFO
            );
          } else if (
            event.affectsConfiguration('java.import.gradle.home') ||
            event.affectsConfiguration('java.import.gradle.version') ||
            event.affectsConfiguration('java.import.gradle.wrapper.enabled')
          ) {
            await this.refresh();
            void this.gradleDaemonsTreeDataProvider.setSpecificVersion(
              getSpecificVersionStatus()
            );
          }
        }
      ),
      vscode.window.onDidCloseTerminal((terminal: vscode.Terminal) => {
        this.taskTerminalsStore.removeTerminal(terminal);
      }),
      vscode.workspace.onDidChangeWorkspaceFolders(() => this.refresh()),
      vscode.window.onDidOpenTerminal((terminal: vscode.Terminal) => {
        this.recentTerminal = terminal;
        this._onDidTerminalOpen.fire(terminal);
      })
    );
  }

  public getApi(): Api {
    return this.api;
  }
}
