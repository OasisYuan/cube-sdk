package com.srain.cube.request;

public interface BeforeRequestHandler {
	public <T> void beforeRequest(SimpleRequest<T> request);
}
