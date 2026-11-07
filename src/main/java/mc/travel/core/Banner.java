package mc.travel.core;

import java.io.IOException;

/**
 * 控制台大字横幅。横幅内容存放在内置资源 banner.txt，生成脚本时会原样写入 start.bat / start.sh。
 */
public final class Banner {

    /** 内置资源缺失时的兜底横幅（纯 ASCII，任何终端都能正常显示）。 */
    private static final String FALLBACK =
            "  _____ ____      _   _   _ _____ _     \n"
                    + " |_   _|  _ \\    / \\ | | | | ____| |    \n"
                    + "   | | | |_) |  / _ \\| | | |  _| | |    \n"
                    + "   | | |  _ <  / ___ \\ |_| | |___| |___ \n"
                    + "   |_| |_| \\_\\/_/   \\_\\___/|_____|_____|\n"
                    + "   __  __  ____ \n"
                    + "  |  \\/  |/ ___|\n"
                    + "  | |\\/| | |    \n"
                    + "  | |  | | |___ \n"
                    + "  |_|  |_|\\____|\n";

    private Banner() {
    }

    public static String text() {
        try {
            String art = Util.readResource("banner.txt");
            if (art.trim().isEmpty()) {
                return FALLBACK;
            }
            return art;
        } catch (IOException e) {
            return FALLBACK;
        }
    }

    public static void print() {
        System.out.println();
        System.out.print(text());
        System.out.println();
        System.out.println("  " + Util.CORE_NAME + " v" + Util.CORE_VERSION
                + "   |   Minecraft " + Util.MC_VERSION
                + "   |   NeoForge + Paper");
        System.out.println("  " + Util.repeat("=", 62));
        System.out.println();
    }
}
