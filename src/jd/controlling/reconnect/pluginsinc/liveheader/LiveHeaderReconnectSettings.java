package jd.controlling.reconnect.pluginsinc.liveheader;

import org.appwork.storage.config.ConfigInterface;
import org.appwork.storage.config.annotations.AboutConfig;
import org.appwork.storage.config.annotations.DefaultBooleanValue;
import org.appwork.storage.config.annotations.DefaultJsonObject;

import jd.controlling.reconnect.pluginsinc.liveheader.remotecall.RouterData;

public interface LiveHeaderReconnectSettings extends ConfigInterface {
    @AboutConfig
    String getScript();

    void setScript(String script);

    @AboutConfig
    String getUserName();

    void setUserName(String str);

    @AboutConfig
    String getPassword();

    void setPassword(String str);

    @AboutConfig
    String getRouterIP();

    void setRouterIP(String str);

    @DefaultJsonObject("{}")
    void setRouterData(RouterData routerData);

    RouterData getRouterData();

    @AboutConfig
    @DefaultBooleanValue(true)
    boolean isAutoSearchBestMatchFilterEnabled();

    void setAutoSearchBestMatchFilterEnabled(boolean b);

    @AboutConfig
    @DefaultBooleanValue(true)
    boolean isAutoReplaceIPEnabled();

    void setAutoReplaceIPEnabled(boolean b);

    @AboutConfig
    String[] getHostWhiteList();

    void setHostWhiteList(final String[] whitelist);

    @AboutConfig
    @org.appwork.storage.config.annotations.DescriptionForConfigEntry("If False, we already tried to send this script to the collect server. Will be resetted each time we change reconnect settings.")
    @DefaultBooleanValue(false)
    boolean isAlreadySendToCollectServer3();

    void setAlreadySendToCollectServer3(boolean b);
}
