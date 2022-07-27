from babushka import AsyncClient

from src.commands.core import CoreCommands
from src.config import ClientConfiguration
from src.utils import to_url


class RedisAsyncClient(CoreCommands):
    @classmethod
    async def create(
        cls, config: ClientConfiguration = ClientConfiguration.get_default_config()
    ):
        self = RedisAsyncClient()
        self.config = config
        self.connection = await self._create_multiplexed_conn()
        return self

    async def _create_multiplexed_conn(self):
        return await AsyncClient.new(to_url(**self.config.config_args))

    async def execute_command(self, command, *args, **kwargs):
        conn_rust_func = getattr(self.connection, command)
        return await conn_rust_func(*args, **kwargs)

    def create_pipeline(self):
        return self.connection.create_pipeline()
