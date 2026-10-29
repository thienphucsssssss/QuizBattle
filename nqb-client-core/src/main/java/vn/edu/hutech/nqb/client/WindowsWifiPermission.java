package vn.edu.hutech.nqb.client;

import com.sun.jna.*;
import com.sun.jna.ptr.*;
import com.sun.jna.win32.StdCallLibrary;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

/** Requests the Windows consent UI; never changes privacy settings or grants consent itself. */
public final class WindowsWifiPermission {
    private WindowsWifiPermission() { }
    public enum Status { ALLOWED, DENIED, UNSUPPORTED, ERROR, TIMED_OUT }
    public record Result(Status status, String diagnostic) { }

    /** The executor must dispatch to the foreground application's UI thread. */
    public static CompletableFuture<Result> requestAccessAsync(Executor ui) {
        if (!Platform.isWindows()) return CompletableFuture.completedFuture(new Result(Status.UNSUPPORTED,"Windows required"));
        return requestAsync(WinRtRequest::new,ui,120_000);
    }

    interface Request extends AutoCloseable {
        int asyncStatus();
        int accessStatus();
        void cancel();
        @Override void close();
    }

    static CompletableFuture<Result> requestAsync(Supplier<Request> factory, Executor ui, long timeoutMillis) {
        var result=new CompletableFuture<Result>();
        var timer=Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon().name("wifi-consent-timer").factory());
        var queued=new java.util.concurrent.atomic.AtomicBoolean();
        Request[] request={null}; // Accessed only on ui, including COM release and RoUninitialize.
        long deadline=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        Runnable close=()->{
            var active=request[0];request[0]=null;
            if(active!=null)try{if(result.isCancelled()||result.getNow(new Result(Status.ERROR,"")).status()==Status.TIMED_OUT)active.cancel();}
                finally{active.close();}
        };
        result.whenComplete((value,error)->{timer.shutdownNow();ui.execute(close);});
        timer.scheduleWithFixedDelay(()->{
            if(result.isDone()||!queued.compareAndSet(false,true))return;
            ui.execute(()->{
                queued.set(false);if(result.isDone()||request[0]==null)return;
                try {
                    int state=request[0].asyncStatus();
                    if(state==1){
                        Status status=switch(request[0].accessStatus()){case 1->Status.ALLOWED;case 2->Status.DENIED;default->Status.ERROR;};
                        result.complete(new Result(status,"Windows Geolocator.RequestAccessAsync"));
                    } else if(state!=0)result.complete(new Result(Status.ERROR,"Windows permission operation failed"));
                    else if(System.nanoTime()>=deadline)result.complete(new Result(Status.TIMED_OUT,"Windows consent timed out"));
                } catch(RuntimeException error){result.complete(new Result(Status.ERROR,"Windows consent check failed"));}
            });
        },50,50,TimeUnit.MILLISECONDS);
        ui.execute(()->{
            if(result.isDone())return;
            try{request[0]=factory.get();if(result.isDone())close.run();}
            catch(LinkageError unavailable){result.complete(new Result(Status.UNSUPPORTED,"Windows consent API unavailable"));}
            catch(RuntimeException error){result.complete(new Result(Status.ERROR,"Windows consent initialization failed: "+error.getMessage()));}
        });
        return result;
    }

    interface Combase extends StdCallLibrary {
        int RoInitialize(int type);
        void RoUninitialize();
        int WindowsCreateString(WString value,int length,PointerByReference result);
        int WindowsDeleteString(Pointer value);
        int RoGetActivationFactory(Pointer runtimeClass,Pointer iid,PointerByReference result);
    }

    /** Geolocator supports unpackaged desktop applications; only permission is requested, never coordinates. */
    private static final class WinRtRequest implements Request {
        final Combase api=Native.load("combase",Combase.class);
        Pointer name,factory,operation,info;
        boolean initialized,closed;
        WinRtRequest() {
            try {
                int hr=api.RoInitialize(0);check(hr);initialized=true;
                var text=new PointerByReference();String runtime="Windows.Devices.Geolocation.Geolocator";
                check(api.WindowsCreateString(new WString(runtime),runtime.length(),text));name=text.getValue();
                var out=new PointerByReference();
                check(api.RoGetActivationFactory(name,guid("9a8e7571-2df5-4591-9f87-eb5fd894e9b7"),out));factory=out.getValue();
                out=new PointerByReference();check(call(factory,6,out));operation=out.getValue();
                out=new PointerByReference();check(call(operation,0,guid("00000036-0000-0000-c000-000000000046"),out));info=out.getValue();
            } catch(RuntimeException|LinkageError error){close();throw error;}
        }
        @Override public int asyncStatus(){var value=new IntByReference();check(call(info,7,value));return value.getValue();}
        @Override public int accessStatus(){var value=new IntByReference();check(call(operation,8,value));return value.getValue();}
        @Override public void cancel(){if(info!=null)call(info,9);}
        @Override public void close(){
            if(closed)return;closed=true;
            try{if(info!=null){call(info,10);call(info,2);}}
            finally{try{if(operation!=null)call(operation,2);}
                finally{try{if(factory!=null)call(factory,2);}
                    finally{try{if(name!=null)api.WindowsDeleteString(name);}finally{if(initialized)api.RoUninitialize();}}}}
        }
    }
    static Memory guid(String value) {
        var uuid=UUID.fromString(value);var memory=new Memory(16);long high=uuid.getMostSignificantBits(),low=uuid.getLeastSignificantBits();
        memory.setInt(0,(int)(high>>>32));memory.setShort(4,(short)(high>>>16));memory.setShort(6,(short)high);
        for(int i=0;i<8;i++)memory.setByte(8+i,(byte)(low>>>(56-8*i)));
        return memory;
    }
    private static int call(Pointer object,int slot,Object... arguments) {
        if(object==null)throw new IllegalStateException("Missing WinRT interface");
        var values=new Object[arguments.length+1];values[0]=object;System.arraycopy(arguments,0,values,1,arguments.length);
        return Function.getFunction(object.getPointer(0).getPointer((long)slot*Native.POINTER_SIZE),Function.ALT_CONVENTION).invokeInt(values);
    }
    private static void check(int hr){if(hr<0)throw new IllegalStateException("WinRT HRESULT 0x"+Integer.toHexString(hr));}
}
