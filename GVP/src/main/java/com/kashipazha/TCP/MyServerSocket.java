package com.kashipazha.TCP;

public abstract class MyServerSocket {
    public MyServerSocket(int portNumber) throws Exception{}

    public abstract MySocket accept() throws Exception;
}