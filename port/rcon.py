"""Minimal RCON client (no dependencies) for the local dev server.

Usage:  python port/rcon.py "<command>" ["<command>" ...]

Used to place the three board games next to spawn so the client actually has to
render them; a renderer that throws only shows up once a block of that type is
on screen, which a plain "the client reached the main menu" smoke test cannot
prove.
"""
import socket
import struct
import sys

HOST = '127.0.0.1'
PORT = 25575
PASSWORD = 'futo'


def pack(req_id, req_type, body):
    payload = struct.pack('<ii', req_id, req_type) + body.encode('utf8') + b'\x00\x00'
    return struct.pack('<i', len(payload)) + payload


def unpack(sock):
    raw = b''
    while len(raw) < 4:
        raw += sock.recv(4 - len(raw))
    (length,) = struct.unpack('<i', raw)
    data = b''
    while len(data) < length:
        data += sock.recv(length - len(data))
    req_id, req_type = struct.unpack('<ii', data[:8])
    return req_id, req_type, data[8:-2].decode('utf8', 'replace')


def main():
    if len(sys.argv) < 2:
        sys.exit('no command given')
    with socket.create_connection((HOST, PORT), timeout=10) as sock:
        sock.sendall(pack(1, 3, PASSWORD))
        req_id, _, _ = unpack(sock)
        if req_id == -1:
            sys.exit('rcon auth failed')
        for i, command in enumerate(sys.argv[1:], start=2):
            sock.sendall(pack(i, 2, command))
            _, _, body = unpack(sock)
            print('> ' + command)
            print('  ' + body.strip())


if __name__ == '__main__':
    main()

