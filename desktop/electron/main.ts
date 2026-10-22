import { app, BrowserWindow, ipcMain, Notification, Tray, Menu, nativeImage, nativeTheme, safeStorage } from 'electron';
import path from 'path';
import os from 'os';
import fs from 'fs';

/**
 * Electron 主进程：窗口、托盘、系统通知、safeStorage 凭据存储、主题跟随系统。
 * 自动更新按设计留二期。
 */

const isDev = !!process.env.VITE_DEV_SERVER_URL;

// 数据目录固定为用户主目录下的 .priority-flow-desktop，须在 app ready 前设置
app.setPath('userData', path.join(os.homedir(), '.priority-flow-desktop'));

let mainWindow: BrowserWindow | null = null;
let tray: Tray | null = null;

/** 应用图标资源路径（开发时取项目 resources/，打包后由 extraResources 复制到安装目录 resources/） */
function resourcePath(name: string): string {
  const root = app.isPackaged ? process.resourcesPath : app.getAppPath();
  return path.join(root, 'resources', name);
}

/** 凭据文件：userData/tokens.json（值经 safeStorage 加密后 base64 存储） */
function tokensFile(): string {
  return path.join(app.getPath('userData'), 'tokens.json');
}

/** 客户端配置：userData/config.json（当前仅服务器地址） */
function configFile(): string {
  return path.join(app.getPath('userData'), 'config.json');
}

function readConfig(): Record<string, string> {
  try {
    return JSON.parse(fs.readFileSync(configFile(), 'utf-8'));
  } catch {
    return {};
  }
}

function writeConfig(config: Record<string, string>): void {
  fs.writeFileSync(configFile(), JSON.stringify(config), 'utf-8');
}

function readTokens(): Record<string, string> {
  try {
    return JSON.parse(fs.readFileSync(tokensFile(), 'utf-8'));
  } catch {
    return {};
  }
}

function writeTokens(tokens: Record<string, string>): void {
  fs.writeFileSync(tokensFile(), JSON.stringify(tokens), 'utf-8');
}

function encrypt(value: string): string {
  if (safeStorage.isEncryptionAvailable()) {
    return safeStorage.encryptString(value).toString('base64');
  }
  // 加密不可用时退化为明文（Linux 部分环境无 keyring）
  return `plain:${value}`;
}

function decrypt(stored: string): string {
  if (stored.startsWith('plain:')) {
    return stored.slice(6);
  }
  return safeStorage.decryptString(Buffer.from(stored, 'base64'));
}

function createWindow(): void {
  mainWindow = new BrowserWindow({
    width: 1440,
    height: 900,
    minWidth: 1080,
    minHeight: 680,
    title: 'Priority Flow',
    icon: process.platform === 'win32' ? resourcePath('icon.ico') : resourcePath('icon.png'),
    backgroundColor: nativeTheme.shouldUseDarkColors ? '#17181c' : '#f6f7f9',
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
    },
  });

  if (isDev) {
    mainWindow.loadURL(process.env.VITE_DEV_SERVER_URL!);
    mainWindow.webContents.openDevTools({ mode: 'detach' });
  } else {
    mainWindow.loadFile(path.join(__dirname, '../dist/index.html'));
  }

  mainWindow.on('closed', () => {
    mainWindow = null;
  });
}

function createTray(): void {
  // Windows 使用多尺寸 ICO，其他平台使用 32px PNG
  const iconPath = process.platform === 'win32' ? resourcePath('icon.ico') : resourcePath('tray.png');
  const icon = nativeImage.createFromPath(iconPath);
  tray = new Tray(icon.isEmpty() ? nativeImage.createEmpty() : icon);
  tray.setToolTip('Priority Flow');
  tray.setContextMenu(
    Menu.buildFromTemplate([
      {
        label: '显示主窗口',
        click: () => {
          if (mainWindow) {
            mainWindow.show();
            mainWindow.focus();
          } else {
            createWindow();
          }
        },
      },
      { type: 'separator' },
      {
        label: '退出',
        click: () => {
          app.quit();
        },
      },
    ]),
  );
  tray.on('double-click', () => {
    if (mainWindow) {
      mainWindow.show();
    }
  });
}

// IPC：安全凭据存取（token 用 safeStorage 加密存本地）
ipcMain.handle('secure-store:set', (_event, key: string, value: string) => {
  const tokens = readTokens();
  tokens[key] = encrypt(value);
  writeTokens(tokens);
});

ipcMain.handle('secure-store:get', (_event, key: string) => {
  const tokens = readTokens();
  const stored = tokens[key];
  return stored ? decrypt(stored) : null;
});

ipcMain.handle('secure-store:clear', () => {
  try {
    fs.rmSync(tokensFile(), { force: true });
  } catch {
    // 忽略文件不存在
  }
});

// IPC：系统通知（桌面通知由客户端收到 WS 推送后调用）
ipcMain.handle('notify', (_event, title: string, body: string) => {
  if (Notification.isSupported()) {
    new Notification({ title, body }).show();
  }
});

// IPC：服务器地址读写（打包后页面为 file:// 源，需绝对地址直连后端）
ipcMain.handle('server-url:get', () => readConfig().serverBaseUrl ?? null);
ipcMain.handle('server-url:set', (_event, url: string) => {
  const config = readConfig();
  config.serverBaseUrl = url;
  writeConfig(config);
});

app.whenReady().then(() => {
  createWindow();
  createTray();

  // 主题跟随系统：变化时通知渲染进程
  nativeTheme.on('updated', () => {
    mainWindow?.webContents.send('theme-changed', nativeTheme.shouldUseDarkColors ? 'dark' : 'light');
  });

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) {
      createWindow();
    }
  });
});

app.on('window-all-closed', () => {
  // 托盘常驻：非 darwin 也不退出（点托盘菜单退出）
});
