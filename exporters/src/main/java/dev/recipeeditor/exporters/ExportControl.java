package dev.recipeeditor.exporters;

import java.util.concurrent.CancellationException;
public final class ExportControl {
    private boolean cancelled,applying;
    public synchronized boolean cancel(){if(applying)return false;cancelled=true;return true;}
    public synchronized void check(){if(cancelled||Thread.currentThread().isInterrupted())throw new CancellationException("Export cancelled before application");}
    public synchronized void beginApply(){check();applying=true;}
    public synchronized boolean applying(){return applying;}
}
