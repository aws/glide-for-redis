# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

from enum import Enum
from typing import Optional

from glide.exceptions import RequestError
from glide.protobuf.command_request_pb2 import CommandRequest, SimpleRoutes
from glide.protobuf.command_request_pb2 import SlotTypes as ProtoSlotTypes


class SlotType(Enum):
    PRIMARY = 1
    # `REPLICA` overrides the `read_from_replica` configuration. If it's used the request
    # will be routed to a replica, even if the strategy is `ALWAYS_FROM_MASTER`.
    REPLICA = 2


class Route:
    def __init__(self) -> None:
        pass


# cluster routes


class AllNodes(Route):
    """
    Route request to all nodes.
    Warning:
        Don't use it with write commands, they could be routed to a replica (RO) node and fail.
    """

    pass


class AllPrimaries(Route):
    pass


class RandomNode(Route):
    """
    Route request to a random node.
    Warning:
        Don't use it with write commands, because they could be randomly routed to a replica (RO) node and fail.
    """

    pass


class SlotKeyRoute(Route):
    def __init__(self, slot_type: SlotType, slot_key: str) -> None:
        super().__init__()
        self.slot_type = slot_type
        self.slot_key = slot_key


class SlotIdRoute(Route):
    def __init__(self, slot_type: SlotType, slot_id: int) -> None:
        super().__init__()
        self.slot_type = slot_type
        self.slot_id = slot_id


class ByAddressRoute(Route):
    def __init__(self, host: str, port: Optional[int] = None) -> None:
        """Routes a request to a node by its address

        Args:
            host (str): The endpoint of the node. If `port` is not provided, should be in the f"{address}:{port}" format, where `address` is the preferred endpoint as shown in the output of the `CLUSTER SLOTS` command.
            port (Optional[int]): The port to access on the node. If port is not provided, `host` is assumed to be in the format f"{address}:{port}".
        """
        super().__init__()
        if port is None:
            split = host.split(":")
            if len(split) < 2:
                raise RequestError(
                    "No port provided, expected host to be formatted as {hostname}:{port}`. Received "
                    + host
                )
            self.host = split[0]
            self.port = int(split[1])
        else:
            self.host = host
            self.port = port


def to_protobuf_slot_type(slot_type: SlotType) -> ProtoSlotTypes.ValueType:
    return (
        ProtoSlotTypes.Primary
        if slot_type == SlotType.PRIMARY
        else ProtoSlotTypes.Replica
    )


def set_protobuf_route(request: CommandRequest, route: Optional[Route]) -> None:
    if route is None:
        return
    elif isinstance(route, AllNodes):
        request.route.simple_routes = SimpleRoutes.AllNodes
    elif isinstance(route, AllPrimaries):
        request.route.simple_routes = SimpleRoutes.AllPrimaries
    elif isinstance(route, RandomNode):
        request.route.simple_routes = SimpleRoutes.Random
    elif isinstance(route, SlotKeyRoute):
        request.route.slot_key_route.slot_type = to_protobuf_slot_type(route.slot_type)
        request.route.slot_key_route.slot_key = route.slot_key
    elif isinstance(route, SlotIdRoute):
        request.route.slot_id_route.slot_type = to_protobuf_slot_type(route.slot_type)
        request.route.slot_id_route.slot_id = route.slot_id
    elif isinstance(route, ByAddressRoute):
        request.route.by_address_route.host = route.host
        request.route.by_address_route.port = route.port
    else:
        raise RequestError(f"Received invalid route type: {type(route)}")
