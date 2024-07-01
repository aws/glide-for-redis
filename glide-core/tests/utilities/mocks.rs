/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */
use futures_intrusive::sync::ManualResetEvent;
use redis::{Cmd, ConnectionAddr, Value};
use std::collections::HashMap;
use std::io;
use std::net::TcpListener;
use std::str::from_utf8;
use std::sync::{
    atomic::{AtomicU16, Ordering},
    Arc,
};
use tokio::io::AsyncWriteExt;
use tokio::net::TcpStream;
use tokio::sync::mpsc::UnboundedSender;

pub struct MockedRequest {
    pub expected_message: String,
    pub response: String,
}

pub struct ServerMock {
    request_sender: UnboundedSender<MockedRequest>,
    address: ConnectionAddr,
    received_commands: Arc<AtomicU16>,
    runtime: Option<tokio::runtime::Runtime>, // option so that we can take the runtime on drop.
    closing_signal: Arc<ManualResetEvent>,
    closing_completed_signal: Arc<ManualResetEvent>,
}

async fn read_from_socket(buffer: &mut Vec<u8>, socket: &mut TcpStream) -> Option<usize> {
    let _ = socket.readable().await;

    loop {
        match socket.try_read_buf(buffer) {
            Ok(0) => {
                return None;
            }
            Ok(size) => return Some(size),
            Err(ref e)
                if e.kind() == io::ErrorKind::WouldBlock
                    || e.kind() == io::ErrorKind::Interrupted =>
            {
                tokio::task::yield_now().await;
                continue;
            }
            Err(_) => {
                return None;
            }
        }
    }
}

async fn receive_and_respond_to_next_message(
    receiver: &mut tokio::sync::mpsc::UnboundedReceiver<MockedRequest>,
    socket: &mut TcpStream,
    received_commands: &Arc<AtomicU16>,
    constant_responses: &HashMap<String, Value>,
    closing_signal: &Arc<ManualResetEvent>,
) -> bool {
    let mut buffer = Vec::with_capacity(1024);
    let size = tokio::select! {
        size = read_from_socket(&mut buffer, socket) => {
            let Some(size) = size else {
                return false;
            };
            size
        },
        _ = closing_signal.wait() => {
            return false;
        }
    };

    let message = from_utf8(&buffer[..size]).unwrap().to_string();
    let setinfo_count = message.matches("SETINFO").count();
    if setinfo_count > 0 {
        let mut buffer = Vec::new();
        for _ in 0..setinfo_count {
            super::encode_value(&Value::Okay, &mut buffer).unwrap();
        }
        socket.write_all(&buffer).await.unwrap();
        return true;
    }

    if let Some(response) = constant_responses.get(&message) {
        let mut buffer = Vec::new();
        super::encode_value(response, &mut buffer).unwrap();
        socket.write_all(&buffer).await.unwrap();
        return true;
    }
    let Ok(request) = receiver.try_recv() else {
        panic!("Received unexpected message: {}", message);
    };
    received_commands.fetch_add(1, Ordering::AcqRel);
    assert_eq!(message, request.expected_message);
    socket.write_all(request.response.as_bytes()).await.unwrap();
    true
}

pub trait Mock {
    fn get_addresses(&self) -> Vec<ConnectionAddr>;

    fn add_response(&self, request: &Cmd, response: String);

    fn get_number_of_received_commands(&self) -> u16;
}

impl ServerMock {
    pub fn new(constant_responses: HashMap<String, Value>) -> Self {
        let listener = super::get_listener_on_available_port();
        Self::new_with_listener(constant_responses, listener)
    }

    pub fn new_with_listener(
        constant_responses: HashMap<String, Value>,
        listener: TcpListener,
    ) -> Self {
        let (request_sender, mut receiver) = tokio::sync::mpsc::unbounded_channel();
        let received_commands = Arc::new(AtomicU16::new(0));
        let received_commands_clone = received_commands.clone();
        let address = ConnectionAddr::Tcp(
            "localhost".to_string(),
            listener.local_addr().unwrap().port(),
        );
        let closing_signal = Arc::new(ManualResetEvent::new(false));
        let closing_signal_clone = closing_signal.clone();
        let closing_completed_signal = Arc::new(ManualResetEvent::new(false));
        let closing_completed_signal_clone = closing_completed_signal.clone();
        let runtime = tokio::runtime::Builder::new_multi_thread()
            .worker_threads(1)
            .thread_name(format!("ServerMock - {address}"))
            .enable_all()
            .build()
            .unwrap();
        runtime.spawn(async move {
            let listener = tokio::net::TcpListener::from_std(listener).unwrap();
            let mut socket = listener.accept().await.unwrap().0;

            while receive_and_respond_to_next_message(
                &mut receiver,
                &mut socket,
                &received_commands_clone,
                &constant_responses,
                &closing_signal_clone,
            )
            .await
            {}

            closing_completed_signal_clone.set();
        });
        Self {
            request_sender,
            address,
            received_commands,
            runtime: Some(runtime),
            closing_signal,
            closing_completed_signal,
        }
    }

    pub async fn close(self) {
        self.closing_signal.set();
        self.closing_completed_signal.wait().await;
    }
}

impl Mock for ServerMock {
    fn get_addresses(&self) -> Vec<ConnectionAddr> {
        vec![self.address.clone()]
    }

    fn add_response(&self, request: &Cmd, response: String) {
        let expected_message = String::from_utf8(request.get_packed_command()).unwrap();
        let _ = self.request_sender.send(MockedRequest {
            expected_message,
            response,
        });
    }

    fn get_number_of_received_commands(&self) -> u16 {
        self.received_commands.load(Ordering::Acquire)
    }
}

impl Drop for ServerMock {
    fn drop(&mut self) {
        self.closing_signal.set();
        self.runtime.take().unwrap().shutdown_background();
    }
}
