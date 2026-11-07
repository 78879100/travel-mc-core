package mc.travel.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

/**
 * Travel MC Core 入口：初始化目录、准备 Java 与引擎、生成启动脚本并自动开服。
 */
public final class Main {

    public static void main(String[] args) {
        Options opt;
        try {
            opt = Options.parse(args);
        } catch (RuntimeException e) {
            System.out.println("[错误] 参数不合法: " + e.getMessage());
            System.out.println("      用 --help 查看全部参数");
            System.exit(1);
            return;
        }
        if (opt.help) {
            printHelp();
            return;
        }
        if (opt.version) {
            System.out.println(Util.CORE_NAME + " v" + Util.CORE_VERSION + "  |  Minecraft " + Util.MC_VERSION
                    + "  |  模组 NeoForge + 插件 Bukkit");
            return;
        }

        Banner.print();

        Path root = resolveRoot(opt);
        try {
            Setup.ensureRoot(root);
            Config cfg = Config.load(root);
            boolean changed = applyOverrides(cfg, opt);

            step("1/6", "检查运行环境");
            System.out.println("  工作目录: " + root);
            System.out.println("  系统: " + System.getProperty("os.name") + " " + System.getProperty("os.arch")
                    + "  |  核心运行在 Java " + Util.javaVersion());
            System.out.println("  目标: Minecraft " + Util.MC_VERSION + "（需要 Java " + Util.MIN_JAVA + " 及以上）");

            step("2/6", "初始化服务器目录");
            Setup.createLayout(root);
            Setup.ensureEula(root);
            Setup.ensureServerProperties(root, cfg);
            Setup.ensureGuide(root);
            System.out.println("  server / mods / plugins / config / world / logs / backups 已就绪");
            System.out.println("  已写入 eula=true（继续使用即表示同意 Minecraft EULA）");

            step("3/6", "准备 Java " + Util.MIN_JAVA + " 运行时");
            String java = JavaEnv.resolve(root, cfg);
            if (java == null) {
                System.out.println();
                System.out.println("  [错误] 没有可用的 Java " + Util.MIN_JAVA + "，服务器无法启动。");
                System.out.println("         安装地址: https://adoptium.net/temurin/releases/?version=" + Util.MIN_JAVA);
                System.exit(1);
                return;
            }
            if (!java.equals(cfg.get("java", "java"))) {
                cfg.set("java", java);
                changed = true;
            }
            System.out.println("  使用: " + java + "（Java " + JavaEnv.versionOf(java) + "）");

            step("4/6", "准备服务端核心");
            if (opt.installNeo) {
                Engine.installNeoForge(root, cfg);
            }
            Engine.Resolved engine = Engine.resolve(root, cfg);
            if (engine == null) {
                printEngineGuidance(root);
                System.exit(2);
                return;
            }
            if (engine.kind == Engine.Kind.NEOFORGE_ARGS) {
                Engine.ensureCrossArgs(root.resolve("server"));
            }
            System.out.println("  引擎: " + engine.displayName);

            step("5/6", "生成启动脚本");
            Scripts.write(root, cfg, engine);
            System.out.println("  start.bat / start.sh 已生成");
            checkCompatibility(root, engine);

            if (changed) {
                cfg.save();
            }

            step("6/6", "启动服务器");
            if (opt.setupOnly || !cfg.getBool("auto-run", true)) {
                printDone(root, engine);
                return;
            }
            int code = launch(root);
            System.exit(code);
        } catch (IOException e) {
            System.out.println();
            System.out.println("  [错误] " + e.getMessage());
            System.exit(1);
        }
    }

    private static Path resolveRoot(Options opt) {
        if (opt.dir != null) {
            return Paths.get(opt.dir).toAbsolutePath();
        }
        String classPath = System.getProperty("java.class.path", "");
        if (classPath.toLowerCase(Locale.ROOT).endsWith(".jar")) {
            Path jar = Paths.get(classPath).toAbsolutePath();
            Path parent = jar.getParent();
            if (parent != null) {
                return parent;
            }
        }
        return Paths.get("").toAbsolutePath();
    }

    private static boolean applyOverrides(Config cfg, Options opt) {
        boolean changed = false;
        if (opt.engine != null) {
            cfg.set("engine", opt.engine);
            changed = true;
        }
        if (opt.memory != null) {
            cfg.set("memory", opt.memory);
            changed = true;
        }
        if (opt.port != null) {
            cfg.set("port", String.valueOf(opt.port));
            changed = true;
        }
        if (opt.javaPath != null) {
            cfg.set("java", opt.javaPath);
            changed = true;
        }
        return changed;
    }

    private static void checkCompatibility(Path root, Engine.Resolved engine) {
        int mods = Setup.countJars(root.resolve("mods"));
        int plugins = Setup.countJars(root.resolve("plugins"));
        if (engine.isHybrid()) {
            System.out.println("  混合核心：可同时加载 " + mods + " 个模组与 " + plugins + " 个插件");
            return;
        }
        if (engine.isNeoForge()) {
            System.out.println("  模组模式：mods/ 里 " + mods + " 个模组会被加载");
            if (plugins > 0) {
                System.out.println("  [提示] 原生 NeoForge 不加载 Bukkit 插件，plugins/ 里的 " + plugins
                        + " 个插件暂不生效");
                System.out.println("         需要插件和模组同时生效，把 Youer 混合核心放进 server/ 即可自动接管");
            }
            return;
        }
        if (engine.isPaper()) {
            System.out.println("  插件模式：plugins/ 里 " + plugins + " 个插件会被加载");
            if (mods > 0) {
                System.out.println("  [提示] Paper 不加载 NeoForge 模组，mods/ 里的 " + mods + " 个模组暂不生效");
            }
            return;
        }
        System.out.println("  自定义核心：" + engine.displayName);
    }

    private static int launch(Path root) throws IOException {
        if (Util.isWindows()) {
            ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", "start", "", "start.bat");
            pb.directory(root.toFile());
            pb.inheritIO();
            Process process = pb.start();
            try {
                process.waitFor();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            System.out.println("  已在新窗口启动服务器，本窗口可以关闭。");
            return 0;
        }
        String shell = Files.isExecutable(Paths.get("/bin/bash")) ? "bash" : "sh";
        ProcessBuilder pb = new ProcessBuilder(shell, "start.sh");
        pb.directory(root.toFile());
        pb.inheritIO();
        Process process = pb.start();
        try {
            return process.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return 130;
        }
    }

    private static void printDone(Path root, Engine.Resolved engine) {
        System.out.println();
        System.out.println("  " + Util.repeat("=", 62));
        System.out.println("  准备完成，双击 " + (Util.isWindows() ? "start.bat" : "start.sh") + " 即可开服");
        System.out.println("  引擎: " + engine.displayName);
        System.out.println("  配置: " + root.resolve(Config.FILE_NAME));
        System.out.println("  模组: " + root.resolve("mods"));
        System.out.println("  插件: " + root.resolve("plugins"));
        System.out.println("  " + Util.repeat("=", 62));
    }

    private static void printEngineGuidance(Path root) {
        System.out.println();
        System.out.println("  [未找到可用的服务端核心]");
        System.out.println("  NeoForge " + Util.MC_VERSION + " 自动安装没有成功，Paper 下载也失败。");
        System.out.println();
        System.out.println("  解决办法（任选其一）：");
        System.out.println("   1) 重试一次: java -jar TravelMC-Core.jar --install-neoforge");
        System.out.println("   2) 把任意服务端 jar 放进 " + root.resolve("server") + " 目录，核心会自动识别");
        System.out.println("      NeoForge 26.3 安装器: https://maven.neoforged.net/releases/net/neoforged/neoforge/");
        System.out.println("      Paper 26.3: https://papermc.io/downloads/paper");
        System.out.println("      Youer 混合端（插件+模组）: https://github.com/MohistMC/Youer/actions");
        System.out.println("   3) 检查 travelmc.properties 里的 neoforge-mirror 是否可用");
        System.out.println();
        System.out.println("  放好后重新运行 java -jar TravelMC-Core.jar 即可，启动脚本会自动重写。");
    }

    private static void step(String index, String title) {
        System.out.println();
        System.out.println("  [" + index + "] " + title);
    }

    private static void printHelp() {
        System.out.println(Util.CORE_NAME + " v" + Util.CORE_VERSION
                + "  |  Minecraft " + Util.MC_VERSION + "  |  模组 NeoForge + 插件 Bukkit");
        System.out.println();
        System.out.println("用法: java -jar TravelMC-Core.jar [参数]");
        System.out.println();
        System.out.println("参数:");
        System.out.println("  --dir <路径>        指定服务器目录（默认使用 jar 所在目录）");
        System.out.println("  --engine <类型>     auto / neoforge / paper，默认 auto");
        System.out.println("  --memory <大小>     内存，例如 4G、8G、4096M");
        System.out.println("  --port <端口>       服务器端口，默认 25565");
        System.out.println("  --java <路径>       指定 java 可执行文件");
        System.out.println("  --setup-only        只生成目录和启动脚本，不启动服务器");
        System.out.println("  --install-neoforge  强制重新安装 NeoForge 原生核心");
        System.out.println("  --version           显示版本");
        System.out.println("  --help              显示帮助");
        System.out.println();
        System.out.println("说明:");
        System.out.println("  首次运行会自动准备 Java " + Util.MIN_JAVA + "、安装 NeoForge " + Util.MC_VERSION
                + "（镜像加速），生成 start.bat / start.sh 并开服。");
        System.out.println("  模组放 mods/，插件放 plugins/；想让插件和模组同时生效，把 Youer 混合核心放进 server/。");
    }

    private static final class Options {
        String dir;
        String engine;
        String memory;
        String javaPath;
        Integer port;
        boolean setupOnly;
        boolean installNeo;
        boolean help;
        boolean version;

        static Options parse(String[] args) {
            Options o = new Options();
            for (int i = 0; i < args.length; i++) {
                String a = args[i];
                if (a.equals("--help") || a.equals("-h")) {
                    o.help = true;
                } else if (a.equals("--version") || a.equals("-v")) {
                    o.version = true;
                } else if (a.equals("--setup-only") || a.equals("--no-run")) {
                    o.setupOnly = true;
                } else if (a.equals("--install-neoforge")) {
                    o.installNeo = true;
                } else if (a.equals("--dir")) {
                    o.dir = requireValue(args, ++i, "--dir");
                } else if (a.startsWith("--dir=")) {
                    o.dir = a.substring("--dir=".length());
                } else if (a.equals("--engine")) {
                    o.engine = requireValue(args, ++i, "--engine");
                } else if (a.startsWith("--engine=")) {
                    o.engine = a.substring("--engine=".length());
                } else if (a.equals("--memory")) {
                    o.memory = requireValue(args, ++i, "--memory");
                } else if (a.startsWith("--memory=")) {
                    o.memory = a.substring("--memory=".length());
                } else if (a.equals("--port")) {
                    o.port = Integer.valueOf(requireValue(args, ++i, "--port"));
                } else if (a.startsWith("--port=")) {
                    o.port = Integer.valueOf(a.substring("--port=".length()));
                } else if (a.equals("--java")) {
                    o.javaPath = requireValue(args, ++i, "--java");
                } else if (a.startsWith("--java=")) {
                    o.javaPath = a.substring("--java=".length());
                } else {
                    throw new IllegalArgumentException("未知参数 " + a);
                }
            }
            return o;
        }

        private static String requireValue(String[] args, int index, String name) {
            if (index >= args.length) {
                throw new IllegalArgumentException(name + " 缺少参数值");
            }
            return args[index];
        }
    }
}
