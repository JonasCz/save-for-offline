package jonas.tool.saveForOffline;

/**
 * Created by jonas on 03/05/15.
 */
public class PageSaver {
    EventCallback eventCallback;
    Options options;

    public PageSaver (EventCallback callback, Options options) {
        this.eventCallback = callback;
        this.options = options;

    }
}

class Options {

}

interface EventCallback {

    public void onProgressChanged (int progress);
    public void onMaxProgressChanged (int maxProgress);
    public void onCurrentFileChanged (String fileName);


}