package org.tfcode.utils;

/**
 * @author fangchuan.tan
 * @createTime 2022年10月20日 17:24:00
 * @Description TODO
 */
public interface Lock {
    boolean tryLock(long timeoutSeconds);

    void unlock();
}
