install-build-tools:
	go install google.golang.org/protobuf/cmd/protoc-gen-go@v1.32.0

install-dev-tools-go1.18.10:
	go install github.com/vakenbolt/go-test-report@v0.9.3
	go install mvdan.cc/gofumpt@v0.4.0
	go install github.com/segmentio/golines@v0.11.0
	go install honnef.co/go/tools/cmd/staticcheck@v0.3.3

install-dev-tools-go1.22.0:
	go install github.com/vakenbolt/go-test-report@v0.9.3
	go install mvdan.cc/gofumpt@v0.6.0
	go install github.com/segmentio/golines@v0.12.2
	go install honnef.co/go/tools/cmd/staticcheck@v0.4.6

install-dev-tools: install-dev-tools-go1.22.0

install-tools-go1.18.10: install-build-tools install-dev-tools-go1.18.10

install-tools-go1.22.0: install-build-tools install-dev-tools-go1.22.0

install-tools: install-tools-go1.22.0

build: build-glide-client generate-protobuf
	go build ./...

build-glide-client:
	cargo build --release

generate-protobuf:
	mkdir -p protobuf
	protoc --proto_path=../glide-core/src/protobuf \
		--go_opt=Mconnection_request.proto=github.com/aws/glide-for-redis/go/protobuf \
		--go_opt=Mredis_request.proto=github.com/aws/glide-for-redis/go/protobuf \
		--go_opt=Mresponse.proto=github.com/aws/glide-for-redis/go/protobuf \
		--go_out=./protobuf \
		--go_opt=paths=source_relative \
		../glide-core/src/protobuf/*.proto

lint:
	go vet ./...
	staticcheck ./...
	gofumpt -d .
	golines --dry-run --shorten-comments -m 127 .

lint-ci: lint
	if [ "$$(gofumpt -l . | wc -l)" -gt 0 ]; then exit 1; fi
	if [ "$$(golines -l --shorten-comments -m 127 . | wc -l)" -gt 0 ]; then exit 1; fi

format:
	gofumpt -w .
	golines -w --shorten-comments -m 127 .

test:
	go test -v -race ./...

test-and-report:
	mkdir -p reports
	go test -v -race ./... -json | go-test-report -o reports/test-report.html
