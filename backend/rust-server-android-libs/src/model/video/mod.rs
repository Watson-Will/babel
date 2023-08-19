use std::fs::File;
use std::pin::Pin;
use std::task::{Context, Poll};
use std::io::{Read, Seek, SeekFrom};
use actix::dev::Stream;
use actix_web::Error;
use actix_web::web::Bytes;
use crate::get_content_type;

pub trait FileStream: futures::Stream<Item=Result<actix_web::web::Bytes, actix_web::Error>> + Unpin {
    fn get_file_length(&self) -> u64;
    fn get_file_type(&self) -> String;
}

pub struct LocalFileStream {
    file: File,
    content_length: u64,
    content_type: String,
    pos: u64,
}

pub struct AndroidUsbStorageFileStream {
    content_length: u64,
    content_type: String,
    pos: u64,
}

impl LocalFileStream {
    pub fn new(path: &str, pos: u64) -> LocalFileStream {
        let file = File::open(&path).unwrap();
        let metadata = file.metadata().unwrap();
        let content_length = metadata.len();
        let content_type = get_content_type(path).unwrap_or("err");

        LocalFileStream {
            file: file,
            content_length: content_length,
            content_type: content_type.to_string(),
            pos: pos
        }
    }
}

impl AndroidUsbStorageFileStream {
    pub async fn new(path: &str, pos: u64, content_length: u64) -> AndroidUsbStorageFileStream {

        let content_type = get_content_type(path).unwrap_or("err");

        AndroidUsbStorageFileStream {
            content_length: content_length,
            content_type: content_type.to_string(),
            pos: pos,
        }
    }
}

impl FileStream for LocalFileStream {
    fn get_file_length(&self) -> u64 {
        return self.content_length
    }

    fn get_file_type(&self) -> String {
        return self.content_type.clone()
    }
}

impl FileStream for AndroidUsbStorageFileStream {
    fn get_file_length(&self) -> u64 {
        self.content_length
    }

    fn get_file_type(&self) -> String {
        self.content_type.clone()
    }
}

impl futures::Stream for LocalFileStream {
    type Item = Result<actix_web::web::Bytes, actix_web::Error>;

    fn poll_next(mut self: Pin<&mut Self>, _cx: &mut Context<'_>) -> Poll<Option<Self::Item>> {
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


impl Stream for AndroidUsbStorageFileStream {
    type Item = Result<actix_web::web::Bytes, actix_web::Error>;

    fn poll_next(self: Pin<&mut Self>, cx: &mut Context<'_>) -> Poll<Option<Self::Item>> {
        return Poll::Ready(None);
    }
}