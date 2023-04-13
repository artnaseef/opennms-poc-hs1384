# Example using grpcurl

    $ grpcurl -plaintext -vv -d '{"query":"hello"}' -import-path src/main/proto/  -proto poc-service.proto localhost:9990 opennms.poc.hs1384.TestService.request
