package vn.edu.hutech.nqb.client;

import org.junit.jupiter.api.Test;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class WindowsWifiPermissionTest {
    static final class FakeRequest implements WindowsWifiPermission.Request {
        final AtomicInteger state=new AtomicInteger();final int result;final CompletableFuture<Void> closed=new CompletableFuture<>();
        final AtomicBoolean cancelled=new AtomicBoolean();Thread owner;
        FakeRequest(int result){this.result=result;}
        void sameThread(){if(owner==null)owner=Thread.currentThread();assertSame(owner,Thread.currentThread(),"COM must stay on UI apartment");}
        public int asyncStatus(){sameThread();return state.get();}
        public int accessStatus(){sameThread();return result;}
        public void cancel(){sameThread();cancelled.set(true);}
        public void close(){sameThread();closed.complete(null);}
    }
    @Test void grantedAndDeniedResultsCloseOnTheUiApartment() throws Exception {
        try(var ui=Executors.newSingleThreadExecutor()) {
            for(int access:new int[]{1,2}) {
                var nativeRequest=new FakeRequest(access);nativeRequest.state.set(1);
                var future=WindowsWifiPermission.requestAsync(()->{nativeRequest.sameThread();return nativeRequest;},ui,2000);
                assertEquals(access==1?WindowsWifiPermission.Status.ALLOWED:WindowsWifiPermission.Status.DENIED,future.get(3,TimeUnit.SECONDS).status());
                nativeRequest.closed.get(2,TimeUnit.SECONDS);assertFalse(nativeRequest.cancelled.get());
            }
        }
    }
    @Test void cancellingPendingConsentCancelsAndReleasesWithoutWaitingForUser() throws Exception {
        try(var ui=Executors.newSingleThreadExecutor()) {
            var created=new CompletableFuture<Void>();var nativeRequest=new FakeRequest(1);
            var future=WindowsWifiPermission.requestAsync(()->{nativeRequest.sameThread();created.complete(null);return nativeRequest;},ui,20_000);
            created.get(2,TimeUnit.SECONDS);future.cancel(false);
            nativeRequest.closed.get(2,TimeUnit.SECONDS);assertTrue(nativeRequest.cancelled.get());
        }
    }
    @Test void timeoutCancelsNativeRequestAndInitializationFailureCompletesCleanly() throws Exception {
        try(var ui=Executors.newSingleThreadExecutor()) {
            var nativeRequest=new FakeRequest(1);
            var future=WindowsWifiPermission.requestAsync(()->{nativeRequest.sameThread();return nativeRequest;},ui,70);
            assertEquals(WindowsWifiPermission.Status.TIMED_OUT,future.get(2,TimeUnit.SECONDS).status());
            nativeRequest.closed.get(2,TimeUnit.SECONDS);assertTrue(nativeRequest.cancelled.get());
            assertEquals(WindowsWifiPermission.Status.ERROR,WindowsWifiPermission.requestAsync(()->{throw new IllegalStateException();},ui,2000).get(2,TimeUnit.SECONDS).status());
        }
    }
}
