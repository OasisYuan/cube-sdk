package com.srain.cube.image.iface;

import java.util.concurrent.Executor;

import com.srain.cube.image.ImageLoader.ImageTaskOrder;

/**
 * An Executor to execute ImageTask, it allows the task can be executed in different order.
 */
public interface ImageTaskExcutor extends Executor {
	public void setTaskOrder(ImageTaskOrder order);
}