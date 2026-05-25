package ru.llacker.lawxgram.helpers;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_bots;

import java.lang.reflect.Type;
import java.util.HashMap;

import ru.llacker.lawxgram.LawxEnvironment;

public class CloudStorageHelper extends AccountInstance {

    private static final CloudStorageHelper[] Instance = new CloudStorageHelper[UserConfig.MAX_ACCOUNT_COUNT];
    private static final Type CLOUD_VALUES_TYPE = new TypeToken<HashMap<String, String>>() {}.getType();

    private final Gson gson = new Gson();

    public CloudStorageHelper(int num) {
        super(num);
    }

    public static CloudStorageHelper getInstance(int num) {
        CloudStorageHelper localInstance = Instance[num];
        if (localInstance == null) {
            synchronized (CloudStorageHelper.class) {
                localInstance = Instance[num];
                if (localInstance == null) {
                    Instance[num] = localInstance = new CloudStorageHelper(num);
                }
            }
        }
        return localInstance;
    }

    private void invokeWebViewCustomMethod(String method, String data, Utilities.Callback2<String, String> callback) {
        invokeWebViewCustomMethod(method, data, true, callback);
    }

    private void invokeWebViewCustomMethod(String method, String data, boolean searchUser, Utilities.Callback2<String, String> callback) {
        var botInfo = LawxEnvironment.getHelperBot();
        if (botInfo == null) {
            runCallback(callback, null, "EMPTY_BOT_INFO");
            return;
        }
        TLRPC.User user = getMessagesController().getUser(botInfo.getId());
        if (user == null) {
            if (searchUser) {
                getUserHelper().resolveUser(botInfo.getUsername(), botInfo.getId(), arg -> invokeWebViewCustomMethod(method, data, false, callback));
            } else {
                runCallback(callback, null, "USER_NOT_FOUND");
            }
            return;
        }
        TL_bots.invokeWebViewCustomMethod req = new TL_bots.invokeWebViewCustomMethod();
        req.bot = getMessagesController().getInputUser(user);
        req.custom_method = method;
        req.params = new TLRPC.TL_dataJSON();
        req.params.data = data;
        getConnectionsManager().sendRequest(req, (res, error) -> {
            if (error != null) {
                runCallback(callback, null, error.text);
            } else if (res instanceof TLRPC.TL_dataJSON) {
                runCallback(callback, ((TLRPC.TL_dataJSON) res).data, null);
            } else {
                runCallback(callback, null, null);
            }
        });
    }

    public void setItem(String key, String value, Utilities.Callback2<String, String> callback) {
        HashMap<String, String> map = new HashMap<>();
        map.put("key", key);
        map.put("value", value);
        invokeWebViewCustomMethod("saveStorageValue", gson.toJson(map), callback);
    }

    public void getItem(String key, Utilities.Callback2<String, String> callback) {
        if (callback == null) {
            return;
        }
        getItems(new String[]{key}, (res, error) -> {
            if (error == null) {
                callback.run(res != null ? res.get(key) : null, null);
            } else {
                callback.run(null, error);
            }
        });
    }

    public void getItems(String[] keys, Utilities.Callback2<HashMap<String, String>, String> callback) {
        if (callback == null) {
            return;
        }
        HashMap<String, String[]> map = new HashMap<>();
        map.put("keys", keys);
        invokeWebViewCustomMethod("getStorageValues", gson.toJson(map), (res, error) -> {
            if (error == null) {
                if (res == null) {
                    callback.run(null, "EMPTY_RESPONSE");
                    return;
                }
                try {
                    HashMap<String, String> values = gson.fromJson(res, CLOUD_VALUES_TYPE);
                    if (values == null) {
                        callback.run(null, "EMPTY_RESPONSE");
                    } else {
                        callback.run(values, null);
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                    callback.run(null, "DECODE_FAILED");
                }
            } else {
                callback.run(null, error);
            }
        });
    }

    public void removeItem(String key, Utilities.Callback2<String, String> callback) {
        removeItems(new String[]{key}, callback);
    }

    public void removeItems(String[] keys, Utilities.Callback2<String, String> callback) {
        HashMap<String, String[]> map = new HashMap<>();
        map.put("keys", keys);
        invokeWebViewCustomMethod("deleteStorageValues", gson.toJson(map), callback);
    }

    public void getKeys(Utilities.Callback2<String[], String> callback) {
        if (callback == null) {
            return;
        }
        invokeWebViewCustomMethod("getStorageKeys", "{}", (res, error) -> {
            if (error == null) {
                if (res == null) {
                    callback.run(null, "EMPTY_RESPONSE");
                    return;
                }
                try {
                    String[] keys = gson.fromJson(res, String[].class);
                    if (keys == null) {
                        callback.run(null, "EMPTY_RESPONSE");
                    } else {
                        callback.run(keys, null);
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                    callback.run(null, "DECODE_FAILED");
                }
            } else {
                callback.run(null, error);
            }
        });
    }

    private static <T> void runCallback(Utilities.Callback2<T, String> callback, T res, String error) {
        if (callback != null) {
            AndroidUtilities.runOnUIThread(() -> callback.run(res, error));
        }
    }
}
