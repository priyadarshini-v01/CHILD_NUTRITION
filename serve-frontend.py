"""Serve the static Nurture UI and same-origin proxy its existing API calls.

Run the Spring Boot app on localhost:8080 first, then run this file and open
http://localhost:8081. This keeps the Java backend and its security settings intact.
"""
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen
import mimetypes
import os
import sys

ROOT = Path(__file__).resolve().parent / "src" / "main" / "resources" / "static"
BACKEND = os.environ.get("CHILD_NUTRITION_API", "http://localhost:8080")
PORT = int(os.environ.get("FRONTEND_PORT", "8081"))


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):
        # Keep normal access output, while never logging request headers/tokens.
        super().log_message(fmt, *args)

    def _proxy(self):
        body = self.rfile.read(int(self.headers.get("Content-Length", "0"))) if self.headers.get("Content-Length") else None
        headers = {key: value for key, value in self.headers.items()
                   if key.lower() not in {"host", "connection", "content-length"}}
        request = Request(BACKEND + self.path, data=body, headers=headers, method=self.command)
        try:
            response = urlopen(request, timeout=60)
            status, payload, response_headers = response.status, response.read(), response.headers
        except HTTPError as error:
            status, payload, response_headers = error.code, error.read(), error.headers
        except (URLError, TimeoutError):
            payload = b'{"message":"The application API is unavailable. Start Spring Boot on port 8080."}'
            status, response_headers = 502, {}
        self.send_response(status)
        for key, value in response_headers.items():
            if key.lower() not in {"connection", "transfer-encoding", "content-encoding", "content-length", "server", "date"}:
                self.send_header(key, value)
        self.send_header("Content-Length", str(len(payload)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        if self.command != "HEAD":
            self.wfile.write(payload)

    def _static(self, head=False):
        relative = "index.html" if self.path.split("?", 1)[0] == "/" else self.path.split("?", 1)[0].lstrip("/")
        target = (ROOT / relative).resolve()
        if not target.is_relative_to(ROOT.resolve()) or not target.is_file():
            self.send_error(404)
            return
        payload = target.read_bytes()
        self.send_response(200)
        self.send_header("Content-Type", mimetypes.guess_type(str(target))[0] or "application/octet-stream")
        self.send_header("Content-Length", str(len(payload)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        if not head:
            self.wfile.write(payload)

    def do_GET(self):
        self._proxy() if self.path.startswith("/api/") else self._static()

    def do_HEAD(self):
        self._proxy() if self.path.startswith("/api/") else self._static(head=True)

    def do_POST(self): self._proxy()
    def do_PUT(self): self._proxy()
    def do_PATCH(self): self._proxy()
    def do_DELETE(self): self._proxy()


if __name__ == "__main__":
    if not ROOT.is_dir():
        sys.exit("Frontend assets are missing. Expected src/main/resources/static.")
    server = ThreadingHTTPServer(("127.0.0.1", PORT), Handler)
    print(f"Nurture frontend: http://localhost:{PORT} (API: {BACKEND})")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nFrontend server stopped.")
    finally:
        server.server_close()
