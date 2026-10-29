package vn.edu.hutech.nqb.client;

import java.nio.file.*;
import java.util.concurrent.CountDownLatch;

/** Child JVM used only for OS-lock/crash and simultaneous-launch integration tests. */
public final class SingleInstanceProcessFixture {
    public static void main(String[] args)throws Exception {
        try(var instance=SingleClientInstance.acquire(Path.of(args[0]))){
            Files.writeString(Path.of(args[1]),instance.isPrimary()?"PRIMARY":"SECONDARY");
            if(instance.isPrimary())new CountDownLatch(1).await();
        }
    }
}
