package org.dllearner.algorithms.ParCEL;

import java.util.concurrent.ThreadFactory;

/**
 * ParCEL worker factory
 * 
 * @author An C. Tran
 *
 */

public class ParCELWorkerThreadFactory implements ThreadFactory {
	private int count=0;
	String idPrefix = "ParCELWorker-";

	@Override
	public Thread newThread(Runnable r) {
		return new Thread(r, idPrefix + (count++));
	}
}
