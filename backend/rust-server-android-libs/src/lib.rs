#![cfg(target_os = "android")]
#![allow(non_snake_case)]

use jni::objects::{JClass, JObject, JString};
use jni::JNIEnv;

use std::{sync::{atomic::AtomicUsize, Arc}, time::Instant};

use crate::model::option_code;
use actix::{Actor, Addr};
use actix_files::NamedFile;
use actix_web::rt::Runtime;
use actix_web::{web, App, Error, HttpRequest, HttpResponse, HttpServer, Responder};
use actix_web_actors::ws;
use android_logger::Config;
use futures::channel::oneshot;
use futures::{SinkExt, TryFutureExt, TryStreamExt};
use jni::sys::{jobject, jstring};
use lazy_static::lazy_static;
use log::{debug, info, Level};
use pnet::datalink::NetworkInterface;
use pnet::util::MacAddr;
use std::collections::HashMap;
use std::fs::File;
use std::io::{Read, Seek, SeekFrom, Write};
use std::net::{Ipv4Addr, ToSocketAddrs};
use std::pin::Pin;
use std::process::Command;
use std::string::String;
use std::sync::Mutex;
use std::task::{Context, Poll};
use std::time::Duration;
use tokio::io::{AsyncBufReadExt, AsyncReadExt, AsyncWriteExt};
use tokio::net::TcpStream;
use uuid::Uuid;

mod connect;
mod model;
mod server;
mod session;

use crate::model::communicate_models;

#[macro_use]
extern crate log;
extern crate android_logger;
extern crate core;

lazy_static! {
    static ref KERNEL_SERVER: Addr<connect::connect_server::ConnectServer> =
        { connect::connect_server::ConnectServer::new().start() };
    static ref CLIENT_SERVER: Addr<server::ClientServer> = {
        let app_state = Arc::new(AtomicUsize::new(0));
        server::ClientServer::new(app_state.clone(), KERNEL_SERVER.clone()).start()
    };
    static ref SERVER_CLIENT_REQUEST_MAP: Arc<Mutex<HashMap<std::string::String, oneshot::Sender<std::string::String>>>> =
        Arc::new(Mutex::new(HashMap::new()));
}

#[no_mangle]
pub extern "C" fn Java_com_swithun_liu_ServerSDK_getTestStr(env: JNIEnv, _: JClass) -> jstring {
    env.new_string("Hello World!")
        .expect("Couldn't create java string!")
        .into_inner()
}

#[no_mangle]
pub extern "C" fn Java_com_swithun_liu_ServerSDK_getTestStrWithInput(
    env: JNIEnv,
    _: JClass,
    input: JString,
) -> jstring {
    let input: String = env
        .get_string(input)
        .expect("Couldn't get java string!")
        .into();
    let output = env
        .new_string(format!("Hello, {}!", input))
        .expect("Couldn't create java string!");
    output.into_inner()
}

#[no_mangle]
pub extern "C" fn Java_com_swithun_liu_ServerSDK_getAllServerInLAN(
    env: JNIEnv,
    _: JClass,
) -> jobject {
    let config = Config::default().with_min_level(Level::Debug);
    android_logger::init_once(config);
    debug!("response # {}", 1);
    let rt = tokio::runtime::Builder::new_multi_thread()
        .worker_threads(50)
        .enable_all()
        .build()
        .unwrap();
    debug!("response # {}", 2);

    let ips: Vec<String> = rt.block_on(async {
        debug!("response # {}", 3);
        scan_network().await
    });
    debug!("response # {}", 4);

    // 找到 Java 中的 String 类
    let java_string_class = env.find_class("java/lang/String").unwrap();

    // 创建一个包含所有元素的 jobjectArray
    let array = env
        .new_object_array(ips.len() as i32, java_string_class, JObject::null())
        .unwrap();

    // 遍历 Vec 中的所有字符串，将它们转换为 Java 中的 String，并将它们添加到 jobjectArray 中
    for (i, s) in ips.iter().enumerate() {
        let java_string = env.new_string(s).unwrap();
        env.set_object_array_element(array, i as i32, java_string.into_inner())
            .unwrap();
    }

    debug!("response # {}", 6);

    array.into()
}

async fn scan_network() -> Vec<String> {
    let mut tasks = vec![];

    let num_cores = num_cpus::get_physical();
    debug!("cpu core num: {}", num_cores);

    for i in 0..=255 {
        let ip = format!("192.168.0.{}", i);
        let clone_ip = ip.clone();

        tasks.push(tokio::spawn(async move {
            let output = Command::new("ping")
                .args(["-c", "1", "-W", "1", &ip])
                .output()
                .expect("Failed to execute command");
            let stdout = String::from_utf8_lossy(&output.stdout);
            if stdout.contains("1 received") {
                if is_server_available(&clone_ip.as_str()).await {
                    debug!("scan_network # add {}", clone_ip);
                    return Some(clone_ip);
                }
            }
            None
        }));
    }

    let results = futures::future::join_all(tasks).await;
    let mut available_ips = Vec::new();
    for res in results {
        if let Some(ip) = res.unwrap() {
            available_ips.push(ip)
        }
    }

    available_ips
}

async fn is_server_available(ip: &str) -> bool {
    let host = format!("{}:8088", ip);
    let mut addr_iter_result = host.to_socket_addrs();
    return match addr_iter_result {
        Ok(mut addr_iter) => match addr_iter.next() {
            Some(addr) => {
                debug!("check {}", host);
                let mut stream_result =
                    std::net::TcpStream::connect_timeout(&addr, Duration::from_millis(600));
                match stream_result {
                    Ok(mut stream) => {
                        debug!("is_server_available get stream");

                        let request = format!(
                            "GET /test HTTP/1.1\r\nHost: {}\r\nConnection: close\r\n\r\n",
                            ip
                        );
                        debug!("is_server_available get stream 2");
                        stream.write(request.as_bytes()).unwrap_or(0);
                        debug!("is_server_available get stream 3");

                        let mut response = String::new();
                        stream.read_to_string(&mut response).unwrap();
                        debug!("is server available : {:?}", response);

                        if response.contains("i am server") {
                            true
                        } else {
                            false
                        }
                    }
                    Err(e) => {
                        debug!("is_server_available err 3 {:?}", e);
                        false
                    }
                }
            }
            None => {
                debug!("is_server_available err 1");
                false
            }
        },
        Err(e) => {
            debug!("is_server_available err 2");
            false
        }
    };
}

async fn index() -> impl Responder {
    NamedFile::open_async("./static/index.html").await.unwrap()
}

#[no_mangle]
pub extern "C" fn Java_com_swithun_liu_ServerSDK_startSever() {
    let config = Config::default().with_min_level(Level::Debug);
    android_logger::init_once(config);

    debug!("rust debug 1");

    let rt = Runtime::new();
    match rt {
        Ok(rt) => {
            debug!("0s");
            rt.block_on(
                async {
                    let a = HttpServer::new(move || {
                        App::new()
                            .app_data(web::Data::new(CLIENT_SERVER.clone()))
                            .service(web::resource("/").to(index))
                            .service(web::resource("/ws").to(transfer))
                            .service(web::resource("/test").to(test))
                            .app_data(web::Data::new(KERNEL_SERVER.clone()))
                            .service(web::resource("/connect").to(connect))
                            .service(web::resource("/get_path_list").route(web::get().to(get_path_list)))
                            .service(web::resource("/get-video").to(get_video))
                    })
                        .workers(2)
                        .bind(("0.0.0.0", 8088));

                    match a {
                        Ok(aa) => {
                            debug!("1s");
                            aa.run().await.expect("haha")
                        }
                        Err(e) => {
                            debug!("1e : {}", {e})
                        }
                    }
                }
            )
        }
        Err(e) => {
            debug!("0e : {}", e);
        }
    }
}

async fn get_video(
    query: web::Query<HashMap<String, String>>,
    req: HttpRequest,
) -> Result<HttpResponse, Error> {
    let temp = "failed path".to_string();
    let path = query.get("path").unwrap_or(&temp);
    info!("get_video / {}", path);

    let file = File::open(&path)?;
    let metadata = file.metadata()?;
    let content_length = metadata.len();
    let content_type = get_content_type(&path.as_str()).unwrap_or("err");
    info!(
        "get_video # content_length: {} content-type: {}",
        content_length,
        content_type.clone()
    );

    // 打印所有请求头
    for (header_name, header_value) in req.headers().iter() {
        debug!("Header: {} => {:?}", header_name, header_value);
    }

    let range = req.headers().get("range").and_then(|header_value| {
        info!("get range: {}", header_value.clone().to_str().ok()?);
        let header_value = header_value.to_str().ok()?;
        let byte_ranges = header_value.trim_start_matches("bytes=").trim();
        info!("1");
        let mut parts = byte_ranges.split('-');
        info!("2");
        let start = parts.next()?.parse().ok()?;
        info!("3");
        let end = parts
            .next()
            .map(|s| s.parse().ok())
            .unwrap_or(Some(content_length as usize - 1))
            .unwrap_or(content_length as usize - 1);
        info!("4");
        info!("start {} end {}", start, end);
        Some((start, end))
    });

    let (start, end) = match range {
        Some((start, end)) => {
            info!("get_video $ start:{} end: {}", start, end);
            if start > content_length as usize || start > end || end >= content_length as usize {
                info!("Invalid range");
                return Err(actix_web::error::ErrorBadRequest("Invalid range"));
            }
            (start, end)
        }
        None => (0, content_length as usize - 1),
    };

    let size = (end - start + 1) as u64;
    Ok(HttpResponse::PartialContent()
        .append_header(("Content-Type", content_type))
        .append_header(("Content-Length", content_length))
        .append_header((
            "Content-Range",
            format!("bytes {}-{}/{}", start, end, content_length),
        ))
        .streaming(Box::pin(FileReaderStream::new(file, start as u64))))
}

struct FileReaderStream {
    file: std::fs::File,
    pos: u64,
}

impl FileReaderStream {
    fn new(file: std::fs::File, pos: u64) -> FileReaderStream {
        FileReaderStream { file, pos }
    }
}

impl futures::Stream for FileReaderStream {
    type Item = Result<actix_web::web::Bytes, actix_web::Error>;

    fn poll_next(mut self: Pin<&mut Self>, cx: &mut Context<'_>) -> Poll<Option<Self::Item>> {
        let mut file = &self.file;
        let mut buf = vec![0u8; 1024 * 1024];

        file.seek(SeekFrom::Start(self.pos as u64))?;

        let n = match file.read(&mut buf) {
            Ok(n) => n,
            Err(ref e) if e.kind() == std::io::ErrorKind::Interrupted => return Poll::Pending,
            Err(e) => return Poll::Ready(Some(Err(actix_web::Error::from(e)))),
        };

        if n == 0 {
            return Poll::Ready(None);
        }

        let bytes = actix_web::web::Bytes::copy_from_slice(&buf[..n]);
        self.pos += n as u64;

        Poll::Ready(Some(Ok(bytes)))
    }
}

fn get_content_type(file_path: &str) -> Option<&'static str> {
    match file_path.split('.').last() {
        Some("html") => Some("text/html"),
        Some("css") => Some("text/css"),
        Some("js") => Some("application/javascript"),
        Some("json") => Some("application/json"),
        Some("jpg") | Some("jpeg") => Some("image/jpeg"),
        Some("png") => Some("image/png"),
        Some("gif") => Some("image/gif"),
        Some("bmp") => Some("image/bmp"),
        Some("ico") => Some("image/x-icon"),
        Some("pdf") => Some("application/pdf"),
        Some("mp4") => Some("video/mp4"),
        Some("mov") => Some("video/quicktime"),
        Some("webm") => Some("video/webm"),
        Some("wmv") => Some("video/x-ms-wmv"),
        Some("mkv") => Some("video/x-matroska"),
        _ => None,
    }
}

async fn get_path_list(query: web::Query<HashMap<String, String>>) -> impl Responder {
    let path_option = query.get("path");

    debug!("rust get_path_list/{}", path_option.clone().unwrap());

    match path_option {
        Some(path) => match path.as_str() {
            "base" => {
                debug!(
                    "get_path_list: base_string, {}",
                    option_code::OptionCode::CommonOptionCode::GET_BASE_PATH_LIST_REQUEST as i32
                );
                let new_uuid: std::string::String = format!("{}", Uuid::new_v4());
                let (tx, rx) = oneshot::channel();

                SERVER_CLIENT_REQUEST_MAP
                    .lock()
                    .unwrap()
                    .insert(new_uuid.clone(), tx);
                let json_struct = communicate_models::CommonCommunicateJsonStruct {
                    uuid: new_uuid,
                    code: option_code::OptionCode::CommonOptionCode::GET_BASE_PATH_LIST_REQUEST
                        as i32,
                    content: "".to_string(),
                    content_type: 0,
                };
                kernel_send_message_to_front_end(json_struct);

                match rx.await {
                    Ok(result) => HttpResponse::Ok().body(result),
                    Err(_) => HttpResponse::InternalServerError().finish(),
                }
            }
            _ => {
                debug!(
                    "get_path_list: {} code {}",
                    path,
                    option_code::OptionCode::CommonOptionCode::GET_CHILDREN_PATH_LIST_REQUEST
                        as i32
                );

                let new_uuid: std::string::String = format!("{}", Uuid::new_v4());
                let (tx, rx) = oneshot::channel();

                SERVER_CLIENT_REQUEST_MAP
                    .lock()
                    .unwrap()
                    .insert(new_uuid.clone(), tx);
                let json_struct = communicate_models::CommonCommunicateJsonStruct {
                    uuid: new_uuid,
                    code: option_code::OptionCode::CommonOptionCode::GET_CHILDREN_PATH_LIST_REQUEST
                        as i32,
                    content: path.to_string(),
                    content_type: 0,
                };
                kernel_send_message_to_front_end(json_struct);

                match rx.await {
                    Ok(result) => HttpResponse::Ok().body(result),
                    Err(_) => HttpResponse::InternalServerError().finish(),
                }
            }
        },
        None => HttpResponse::Ok().body("err 1".to_string()),
    }
}

async fn transfer(
    req: HttpRequest,
    stream: web::Payload,
    srv: web::Data<Addr<server::ClientServer>>,
) -> Result<HttpResponse, Error> {
    let session = session::ClientSession {
        id: 0,
        hb: Instant::now(),
        name: None,
        transfer_server: srv.get_ref().clone(),
    };

    ws::start(session, &req, stream)
}

async fn test() -> String {
    debug!("somebody test");
    "i am server".to_string()
}

async fn connect(
    req: HttpRequest,
    stream: web::Payload,
    srv: web::Data<Addr<connect::connect_server::ConnectServer>>,
) -> Result<HttpResponse, Error> {
    let session = connect::connect_session::ConnectSession {
        hb: Instant::now(),
        connect_server: srv.get_ref().clone(),
    };

    ws::start(session, &req, stream)
}

pub fn kernel_send_message_to_front_end(
    json_struct: communicate_models::CommonCommunicateJsonStruct,
) {
    let json_struct_str = serde_json::to_string(&json_struct).unwrap();

    KERNEL_SERVER.do_send(connect::connect_server::KernelToFrontEndMessage {
        msg: json_struct_str,
    })
}

pub fn handle_android_front_end_response(
    kernel_and_front_end_json: communicate_models::CommonCommunicateJsonStruct,
) {
    let uuid = kernel_and_front_end_json.uuid;
    let result = kernel_and_front_end_json.content;
    let mut request_map = SERVER_CLIENT_REQUEST_MAP.lock().unwrap();
    match request_map.remove(&uuid) {
        Some(tx) => {
            let _ = tx.send(result);
        }
        None => {
            eprintln!("Failed to find request with uuid: {}", uuid);
        }
    }
}

