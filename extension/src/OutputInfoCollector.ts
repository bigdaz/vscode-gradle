/* --------------------------------------------------------------------------------------------
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 * ------------------------------------------------------------------------------------------ */

import * as vscode from 'vscode';

export class OutputInfoCollector implements vscode.OutputChannel {
  private channel: vscode.OutputChannel;

  constructor(public name: string) {
    this.channel = vscode.window.createOutputChannel(this.name);
  }

  append(value: string): void {
    this.channel.append(value);
  }

  appendLine(value: string): void {
    this.channel.appendLine(value);
  }

  clear(): void {
    this.channel.clear();
  }

  show(preserveFocus?: boolean): void;
  show(column?: vscode.ViewColumn, preserveFocus?: boolean): void;
  show(column?: any, preserveFocus?: any): void {
    this.channel.show(column, preserveFocus);
  }

  hide(): void {
    this.channel.hide();
  }

  dispose(): void {
    this.channel.dispose();
  }
}
