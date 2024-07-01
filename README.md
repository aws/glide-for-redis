# GLIDE for Redis
General Language Independent Driver for the Enterprise (GLIDE) for Redis, is an AWS-sponsored, open-source Redis client. GLIDE for Redis works with any Redis distribution that adheres to the Redis Serialization Protocol (RESP) specification, including open-source Redis, Amazon ElastiCache for Redis, and Amazon MemoryDB for Redis.
Strategic, mission-critical Redis-based applications have requirements for security, optimized performance, minimal downtime, and observability.  GLIDE for Redis is designed to provide a client experience that helps meet these objectives. It is sponsored and supported by AWS, and comes pre-configured with best practices learned from over a decade of operating Redis-compatible services used by hundreds of thousands of customers. To help ensure consistency in development and operations, GLIDE for Redis is implemented using a core driver framework, written in Rust, with extensions made available for each supported programming language. This design ensures that updates easily propagate to each language and reduces overall complexity. In this Preview release, GLIDE for Redis is available for Python and Javascript (Node.js), with support for Java actively under development.

## Supported Engine Versions
GLIDE for Redis is API-compatible with the following engine versions:

| Engine Type        | Version |
|--------------------|---------|
| Valkey and Redis   | 7.2     |
| Valkey and Redis   | 7.0     |
| Valkey and Redis   | 6.2     |

## Current Status
We've made GLIDE for Redis an open-source project, and are releasing it in Preview to the community to gather feedback, and actively collaborate on the project roadmap. We welcome questions and contributions from all Redis stakeholders.
This preview release is recommended for testing purposes only. It is available in Python and Javascript (Node.js), with Java to follow. We're tracking its production readiness and future features on the [roadmap](https://github.com/orgs/aws/projects/187/).


## Getting Started

-   [Node](./node/README.md)
-   [Python](./python/README.md)
-   [Java](./java/README.md)
-   [Documentation](https://github.com/aws/glide-for-redis/wiki)

## Getting Help
If you have any questions, feature requests, encounter issues, or need assistance with this project, please don't hesitate to open a GitHub issue. Our community and contributors are here to help you. Before creating an issue, we recommend checking the [existing issues](https://github.com/aws/glide-for-redis/issues) to see if your question or problem has already been addressed. If not, feel free to create a new issue, and we'll do our best to assist you. Please provide as much detail as possible in your issue description, including:

1. A clear and concise title
2. Detailed description of the problem or question
3. A reproducible test case or series of steps
4. The GLIDE for Redis version in use
5. Operating system
6. Redis version
7. Redis cluster information, cluster topology, number of shards, number of replicas, used data types
8. Any modifications you've made that are relevant to the issue
9. Anything unusual about your environment or deployment
10. Log files


## Contributing

GitHub is a platform for collaborative coding. If you're interested in writing code, we encourage you to contribute by submitting pull requests from forked copies of this repository. Additionally, please consider creating GitHub issues for reporting bugs and suggesting new features. Feel free to comment on issues that interest. For more info see [Contributing](./CONTRIBUTING.md).

## License
* [Apache License 2.0](./LICENSE)
