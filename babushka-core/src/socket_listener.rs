use super::{headers::*, rotating_buffer::RotatingBuffer};
use byteorder::{LittleEndian, WriteBytesExt};
use bytes::BufMut;
use dispose::{Disposable, Dispose};
use futures::stream::StreamExt;
use logger_core::{log_error, log_info};
use num_traits::ToPrimitive;
#[cfg(not(feature = "bcore-connection"))]
use redis::aio::MultiplexedConnection;
#[cfg(not(feature = "bcore-connection"))]
use redis::Client;
#[cfg(feature = "bcore-connection")]
use crate::async_connections::SimpleAsyncConnection;
use redis::{AsyncCommands, RedisResult, Value};
use redis::RedisError;
use signal_hook::consts::signal::*;
use signal_hook_tokio::Signals;
use std::cell::Cell;
use std::mem::size_of;
use std::ops::Range;
use std::rc::Rc;
use std::str;
use std::{io, thread};
use tokio::io::ErrorKind::AddrInUse;
use tokio::net::{UnixListener, UnixStream};
use tokio::runtime::Builder;
use tokio::sync::mpsc::{channel, Sender};
use tokio::sync::Mutex;
use tokio::task;
use ClosingReason::*;
use PipeListeningResult::*;

/// The socket file name
pub const SOCKET_FILE_NAME: &str = "babushka-socket";

#[cfg(feature = "bcore-connection")]
macro_rules! connection_type {
    () => {SimpleAsyncConnection};
}

#[cfg(not(feature = "bcore-connection"))]
macro_rules! connection_type {
    () => {MultiplexedConnection}
}

/// struct containing all objects needed to bind to a socket and clean it.
struct SocketListener {
    socket_path: String,
    cleanup_socket: bool,
}

impl Dispose for SocketListener {
    fn dispose(self) {
        if self.cleanup_socket {
            close_socket(self.socket_path);
        }
    }
}

/// struct containing all objects needed to read from a unix stream.
struct UnixStreamListener {
    read_socket: Rc<UnixStream>,
    rotating_buffer: RotatingBuffer,
}

/// struct containing all objects needed to write to a socket.
struct Writer {
    socket: Rc<UnixStream>,
    lock: Mutex<()>,
    accumulated_outputs: Cell<Vec<u8>>,
    closing_sender: Sender<ClosingReason>,
}

enum PipeListeningResult {
    Closed(ClosingReason),
    ReceivedValues(Vec<WholeRequest>),
}

impl From<ClosingReason> for PipeListeningResult {
    fn from(result: ClosingReason) -> Self {
        Closed(result)
    }
}

impl UnixStreamListener {
    fn new(read_socket: Rc<UnixStream>) -> Self {
        // if the logger has been initialized by the user (external or internal) on info level this log will be shown
        log_info("connection", "new socket listener initiated");
        let rotating_buffer = RotatingBuffer::new(2, 65_536);
        Self {
            read_socket,
            rotating_buffer,
        }
    }

    pub(crate) async fn next_values(&mut self) -> PipeListeningResult {
        loop {
            if let Err(err) = self.read_socket.readable().await {
                return ClosingReason::UnhandledError(err.into()).into();
            }

            let read_result = self
                .read_socket
                .try_read_buf(self.rotating_buffer.current_buffer());
            match read_result {
                Ok(0) => {
                    return ReadSocketClosed.into();
                }
                Ok(_) => {
                    return match self.rotating_buffer.get_requests() {
                        Ok(requests) => ReceivedValues(requests),
                        Err(err) => UnhandledError(err.into()).into(),
                    };
                }
                Err(ref e)
                    if e.kind() == io::ErrorKind::WouldBlock
                        || e.kind() == io::ErrorKind::Interrupted =>
                {
                    continue;
                }
                Err(err) => return UnhandledError(err.into()).into(),
            }
        }
    }
}

async fn write_to_output(writer: &Rc<Writer>) {
    let Ok(_guard) = writer.lock.try_lock() else {
        return;
    };

    let mut output = writer.accumulated_outputs.take();
    loop {
        if output.is_empty() {
            return;
        }
        let mut total_written_bytes = 0;
        while total_written_bytes < output.len() {
            if let Err(err) = writer.socket.writable().await {
                let _ = writer.closing_sender.send(err.into()).await; // we ignore the error, because it means that the reader was dropped, which is ok.
                return;
            }
            match writer.socket.try_write(&output[total_written_bytes..]) {
                Ok(written_bytes) => {
                    total_written_bytes += written_bytes;
                }
                Err(err)
                    if err.kind() == io::ErrorKind::WouldBlock
                        || err.kind() == io::ErrorKind::Interrupted =>
                {
                    continue;
                }
                Err(err) => {
                    let _ = writer.closing_sender.send(err.into()).await; // we ignore the error, because it means that the reader was dropped, which is ok.
                }
            }
        }
        output.clear();
        output = writer.accumulated_outputs.replace(output);
    }
}

fn write_response_header(
    accumulated_outputs: &Cell<Vec<u8>>,
    callback_index: u32,
    response_type: ResponseType,
    length: usize,
) -> Result<(), io::Error> {
    let mut vec = accumulated_outputs.take();
    vec.put_u32_le(length as u32);
    vec.put_u32_le(callback_index);
    vec.put_u32_le(response_type.to_u32().ok_or_else(|| {
        io::Error::new(
            io::ErrorKind::InvalidData,
            format!("Response type {response_type:?} wasn't found"),
        )
    })?);

    assert!(!vec.is_empty());
    accumulated_outputs.set(vec);
    Ok(())
}

fn write_null_response_header(
    accumulated_outputs: &Cell<Vec<u8>>,
    callback_index: u32,
) -> Result<(), io::Error> {
    write_response_header(
        accumulated_outputs,
        callback_index,
        ResponseType::Null,
        HEADER_END,
    )
}

fn write_pointer_to_output<T>(accumulated_outputs: &Cell<Vec<u8>>, pointer_to_write: *mut T) {
    let mut vec = accumulated_outputs.take();
    vec.write_u64::<LittleEndian>(pointer_to_write as u64)
        .unwrap();
    accumulated_outputs.set(vec);
}

async fn send_set_request(
    buffer: SharedBuffer,
    key_range: Range<usize>,
    value_range: Range<usize>,
    callback_index: u32,
    mut connection: connection_type!(),
    writer: Rc<Writer>,
) -> RedisResult<()> {
    connection
        .set(&buffer[key_range], &buffer[value_range])
        .await?;
    write_null_response_header(&writer.accumulated_outputs, callback_index)?;
    write_to_output(&writer).await;
    Ok(())
}

fn write_redis_value(
    value: Value,
    callback_index: u32,
    writer: &Rc<Writer>,
) -> Result<(), io::Error> {
    if let Value::Nil = value {
        // Since null values don't require any additional data, they can be sent without any extra effort.
        write_null_response_header(&writer.accumulated_outputs, callback_index)?;
    } else {
        write_response_header(
            &writer.accumulated_outputs,
            callback_index,
            ResponseType::Value,
            HEADER_END + size_of::<usize>(),
        )?;
        // Move the value to the heap and leak it. The wrapper should use `Box::from_raw` to recreate the box, use the value, and drop the allocation.
        let pointer = Box::leak(Box::new(value));
        write_pointer_to_output(&writer.accumulated_outputs, pointer);
    }
    Ok(())
}

async fn send_get_request(
    vec: SharedBuffer,
    key_range: Range<usize>,
    callback_index: u32,
    mut connection: connection_type!(),
    writer: Rc<Writer>,
) -> RedisResult<()> {
    let result: Value = connection.get(&vec[key_range]).await?;
    write_redis_value(result, callback_index, &writer)?;
    write_to_output(&writer).await;
    Ok(())
}

fn handle_request(
    request: WholeRequest,
    connection: connection_type!(),
    writer: Rc<Writer>) {
    task::spawn_local(async move {
        let result = match request.request_type {
            RequestRanges::Get { key: key_range } => {
                send_get_request(
                    request.buffer,
                    key_range,
                    request.callback_index,
                    connection,
                    writer.clone(),
                )
                .await
            }
            RequestRanges::Set {
                key: key_range,
                value: value_range,
            } => {
                send_set_request(
                    request.buffer,
                    key_range,
                    value_range,
                    request.callback_index,
                    connection,
                    writer.clone(),
                )
                .await
            }
            RequestRanges::ServerAddress { address: _ } => {
                unreachable!("Server address can only be sent once")
            }
        };
        if let Err(err) = result {
            write_error(
                &err.to_string(),
                request.callback_index,
                writer,
                ResponseType::RequestError,
            )
            .await;
        }
    });
}

async fn write_error(
    err: &String,
    callback_index: u32,
    writer: Rc<Writer>,
    response_type: ResponseType,
) {
    let length = HEADER_END + size_of::<usize>();
    write_response_header(
        &writer.accumulated_outputs,
        callback_index,
        response_type,
        length,
    )
    .expect("Failed writing error to vec");
    // Move the error string to the heap and leak it. The wrapper should use `Box::from_raw` to recreate the box, use the error string, and drop the allocation.
    write_pointer_to_output(
        &writer.accumulated_outputs,
        Box::leak(Box::new(err.to_string())),
    );
    write_to_output(&writer).await;
}

async fn handle_requests(
    received_requests: Vec<WholeRequest>,
    connection: &connection_type!(),
    writer: &Rc<Writer>,
) {
    // TODO - can use pipeline here, if we're fine with the added latency.
    for request in received_requests {
        handle_request(request, connection.clone(), writer.clone())
    }
    // Yield to ensure that the subtasks aren't starved.
    task::yield_now().await;
}

fn close_socket(socket_path: String) {
    let _ = std::fs::remove_file(socket_path);
}

fn to_babushka_result<T, E: std::fmt::Display>(
    result: Result<T, E>,
    err_msg: Option<&str>,
) -> Result<T, ClientCreationError> {
    result.map_err(|err: E| {
        ClientCreationError::UnhandledError(match err_msg {
            Some(msg) => format!("{msg}: {err}"),
            None => format!("{err}"),
        })
    })
}

#[cfg(feature = "bcore-connection")]
async fn create_connection(connection_address: &str) -> Result<SimpleAsyncConnection, ClientCreationError>  {
    to_babushka_result(
        SimpleAsyncConnection::open(connection_address).await, 
        Some("Failed to create a multiplexed connection"))
}

#[cfg(not(feature = "bcore-connection"))]
async fn create_connection(connection_address: &str) -> Result<MultiplexedConnection, ClientCreationError> {
    let client = to_babushka_result(
        Client::open(connection_address),
        Some("Failed to open redis-rs client"),
    )?;
    let connection = to_babushka_result(
        client.get_multiplexed_async_connection().await,
        Some("Failed to create a multiplexed connection"),
    )?;
    Ok(connection)
}

async fn parse_address_create_conn (
    writer: &Rc<Writer>,
    request: &WholeRequest,
    address_range: Range<usize>,
) -> Result<connection_type!(), ClientCreationError> {
    let address = &request.buffer[address_range];
    let address = to_babushka_result(
        std::str::from_utf8(address),
        Some("Failed to parse address"),
    )?;
    let connection = create_connection(address).await?;

    // Send response
    write_null_response_header(&writer.accumulated_outputs, request.callback_index)
        .expect("Failed writing address response.");
    write_to_output(writer).await;
    Ok::<connection_type!(), ClientCreationError>(connection)
}

async fn wait_for_server_address_create_conn(
    client_listener: &mut UnixStreamListener,
    writer: &Rc<Writer>,
) -> Result<connection_type!(), ClientCreationError> {
    // Wait for the server's address
    match client_listener.next_values().await {
        Closed(reason) => Err(ClientCreationError::SocketListenerClosed(reason)),
        ReceivedValues(received_requests) => {
            if let Some(request) = received_requests.first() {
                match request.request_type.clone() {
                    RequestRanges::ServerAddress {
                        address: address_range,
                    } => parse_address_create_conn(writer, request, address_range).await,
                    _ => Err(ClientCreationError::UnhandledError(
                        "Received another request before receiving server address".to_string(),
                    )),
                }
            } else {
                Err(ClientCreationError::UnhandledError(
                    "No received requests".to_string(),
                ))
            }
        }
    }
}

async fn read_values_loop(
    mut client_listener: UnixStreamListener,
    connection: connection_type!(),
    writer: Rc<Writer>,
) -> ClosingReason {
    loop {
        match client_listener.next_values().await {
            Closed(reason) => {
                return reason;
            }
            ReceivedValues(received_requests) => {
                handle_requests(received_requests, &connection, &writer).await;
            }
        }
    }
}

async fn listen_on_client_stream(socket: UnixStream) {
    let socket = Rc::new(socket);
    // Spawn a new task to listen on this client's stream
    let write_lock = Mutex::new(());
    let mut client_listener = UnixStreamListener::new(socket.clone());
    let accumulated_outputs = Cell::new(Vec::new());
    let (sender, mut receiver) = channel(1);
    let writer = Rc::new(Writer {
        socket,
        lock: write_lock,
        accumulated_outputs,
        closing_sender: sender,
    });
    let connection = match wait_for_server_address_create_conn(&mut client_listener, &writer).await
    {
        Ok(conn) => conn,
        Err(ClientCreationError::SocketListenerClosed(reason)) => {
            let error_message = format!("Socket listener closed due to {reason:?}");
            write_error(&error_message, u32::MAX, writer, ResponseType::ClosingError).await;
            log_error("client creation", error_message);
            return; // TODO: implement error protocol, handle closing reasons different from ReadSocketClosed
        }
        Err(ClientCreationError::UnhandledError(err)) => {
            write_error(
                &err.to_string(),
                u32::MAX,
                writer,
                ResponseType::ClosingError,
            )
            .await;
            log_error("client creation", format!("Recieved error: {err}"));
            return; // TODO: implement error protocol
        }
    };
    tokio::select! {
            reader_closing = read_values_loop(client_listener, connection, writer.clone()) => {
                if let ClosingReason::UnhandledError(err) = reader_closing {
                    write_error(&err.to_string(), u32::MAX, writer, ResponseType::ClosingError).await;
                };
            },
            writer_closing = receiver.recv() => {
                if let Some(ClosingReason::UnhandledError(err)) = writer_closing {
                    log_error("writer closing", err.to_string());
                }
            }
    }
}

impl SocketListener {
    fn new() -> Self {
        SocketListener {
            socket_path: get_socket_path(),
            cleanup_socket: true,
        }
    }

    pub(crate) async fn listen_on_socket<InitCallback>(&mut self, init_callback: InitCallback)
    where
        InitCallback: FnOnce(Result<String, String>) + Send + 'static,
    {
        // Bind to socket
        let listener = match UnixListener::bind(self.socket_path.clone()) {
            Ok(listener) => listener,
            Err(err) if err.kind() == AddrInUse => {
                init_callback(Ok(self.socket_path.clone()));
                // Don't cleanup the socket resources since the socket is being used
                self.cleanup_socket = false;
                return;
            }
            Err(err) => {
                init_callback(Err(err.to_string()));
                return;
            }
        };
        let local = task::LocalSet::new();
        init_callback(Ok(self.socket_path.clone()));
        local
            .run_until(async move {
                loop {
                    tokio::select! {
                        listen_v = listener.accept() => {
                            if let Ok((stream, _addr)) = listen_v {
                                // New client
                                task::spawn_local(listen_on_client_stream(stream));
                            } else if listen_v.is_err() {
                                return
                            }
                        },
                        // Interrupt was received, close the socket
                        _ = handle_signals() => return
                    }
                }
            })
            .await;
    }
}

#[derive(Debug)]
/// Enum describing the reason that a socket listener stopped listening on a socket.
pub enum ClosingReason {
    /// The socket was closed. This is the expected way that the listener should be closed.
    ReadSocketClosed,
    /// The listener encounter an error it couldn't handle.
    UnhandledError(RedisError),
}

impl From<io::Error> for ClosingReason {
    fn from(error: io::Error) -> Self {
        UnhandledError(error.into())
    }
}

/// Enum describing errors received during client creation.
pub enum ClientCreationError {
    /// An error was returned during the client creation process.
    UnhandledError(String),
    /// Socket listener was closed before receiving the server address.
    SocketListenerClosed(ClosingReason),
}

/// Get the socket path as a string
fn get_socket_path() -> String {
    let socket_name = format!("{}-{}", SOCKET_FILE_NAME, std::process::id());
    std::env::temp_dir()
        .join(socket_name)
        .into_os_string()
        .into_string()
        .expect("Couldn't create socket path")
}

async fn handle_signals() {
    // Handle Unix signals
    let mut signals =
        Signals::new([SIGTERM, SIGQUIT, SIGINT, SIGHUP]).expect("Failed creating signals");
    loop {
        if let Some(signal) = signals.next().await {
            match signal {
                SIGTERM | SIGQUIT | SIGINT | SIGHUP => {
                    log_info("connection", format!("Signal {signal:?} received"));
                    return;
                }
                _ => continue,
            }
        }
    }
}

/// Creates a new thread with a main loop task listening on the socket for new connections.
/// Every new connection will be assigned with a client-listener task to handle their requests.
///
/// # Arguments
/// * `init_callback` - called when the socket listener fails to initialize, with the reason for the failure.
pub fn start_socket_listener<InitCallback>(init_callback: InitCallback)
where
    InitCallback: FnOnce(Result<String, String>) + Send + 'static,
{
    thread::Builder::new()
        .name("socket_listener_thread".to_string())
        .spawn(move || {
            let runtime = Builder::new_current_thread()
                .enable_all()
                .thread_name("socket_listener_thread")
                .build();
            match runtime {
                Ok(runtime) => {
                    let mut listener = Disposable::new(SocketListener::new());
                    runtime.block_on(listener.listen_on_socket(init_callback));
                }
                Err(err) => init_callback(Err(err.to_string())),
            };
        })
        .expect("Thread spawn failed. Cannot report error because callback was moved.");
}
