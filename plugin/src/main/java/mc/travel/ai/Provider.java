package mc.travel.ai;

/**
 * 一个 AI 服务商：安装模块后才知道它存在。
 */
public final class Provider {

    public final String id;
    public final String display;
    public String baseUrl;
    public String model;
    public final String keyPage;
    public boolean installed;

    public Provider(String id, String display, String baseUrl, String model, String keyPage) {
        this.id = id;
        this.display = display;
        this.baseUrl = baseUrl;
        this.model = model;
        this.keyPage = keyPage;
    }
}
