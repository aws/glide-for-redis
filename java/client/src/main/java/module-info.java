module glide.api {
    exports glide.api;
    exports glide.api.commands;
    exports glide.api.models;
    exports glide.api.models.commands;
    exports glide.api.models.commands.bitmap;
    exports glide.api.models.commands.geospatial;
    exports glide.api.models.commands.function;
    exports glide.api.models.commands.scan;
    exports glide.api.models.commands.stream;
    exports glide.api.models.configuration;
    exports glide.api.models.exceptions;

    requires com.google.protobuf;
    requires io.netty.codec;
    requires io.netty.common;
    requires io.netty.transport;
    requires io.netty.transport.classes.epoll;
    requires io.netty.transport.classes.kqueue;
    requires io.netty.transport.unix.common;
    requires lombok;
    requires org.apache.commons.lang3;
}
