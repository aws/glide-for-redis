#### Changes
* Node: Added ZUNIONSTORE command ([#1550](https://github.com/aws/glide-for-redis/pull/1550))
* Node: Added ZINTERSTORE command ([#1513](https://github.com/aws/glide-for-redis/pull/1513))
* Python: Added OBJECT ENCODING command ([#1471](https://github.com/aws/glide-for-redis/pull/1471))
* Python: Added OBJECT FREQ command ([#1472](https://github.com/aws/glide-for-redis/pull/1472))
* Python: Added OBJECT IDLETIME command ([#1474](https://github.com/aws/glide-for-redis/pull/1474))
* Node: Added RENAMENX command ([#1483](https://github.com/aws/glide-for-redis/pull/1483))
* Python: Added OBJECT REFCOUNT command ([#1485](https://github.com/aws/glide-for-redis/pull/1485))
* Python: Added RENAMENX command ([#1492](https://github.com/aws/glide-for-redis/pull/1492))
* Python: Added PFCOUNT command ([#1493](https://github.com/aws/glide-for-redis/pull/1493))
* Python: Added PFMERGE command ([#1497](https://github.com/aws/glide-for-redis/pull/1497))
* Node: Added SINTER command ([#1500](https://github.com/aws/glide-for-redis/pull/1500))
* Python: Added XLEN command ([#1503](https://github.com/aws/glide-for-redis/pull/1503))
* Python: Added LASTSAVE command ([#1509](https://github.com/aws/glide-for-redis/pull/1509))
* Python: Added GETDEL command ([#1514](https://github.com/aws/glide-for-redis/pull/1514))
* Python: Added ZINTER, ZUNION commands ([#1478](https://github.com/aws/glide-for-redis/pull/1478))
* Python: Added SINTERCARD command ([#1511](https://github.com/aws/glide-for-redis/pull/1511))
* Python: Added SORT command ([#1439](https://github.com/aws/glide-for-redis/pull/1439))
* Node: Added OBJECT ENCODING command ([#1518](https://github.com/aws/glide-for-redis/pull/1518))
* Python: Added LMOVE and BLMOVE commands ([#1536](https://github.com/aws/glide-for-redis/pull/1536))
* Node: Added SUNIONSTORE command ([#1549](https://github.com/aws/glide-for-redis/pull/1549))
* Node: Added PFCOUNT command ([#1545](https://github.com/aws/glide-for-redis/pull/1545))
* Node: Added OBJECT FREQ command ([#1542](https://github.com/aws/glide-for-redis/pull/1542))
* Node: Added LINSERT command ([#1544](https://github.com/aws/glide-for-redis/pull/1544))
* Node: Added XLEN command ([#1555](https://github.com/aws/glide-for-redis/pull/1555))
* Node: Added ZINTERCARD command ([#1553](https://github.com/aws/glide-for-redis/pull/1553))
* Python: Added LMPOP and BLMPOP commands ([#1547](https://github.com/aws/glide-for-redis/pull/1547))

### Breaking Changes
* Node: Update XREAD to return a Map of Map ([#1494](https://github.com/aws/glide-for-redis/pull/1494))

## 0.4.1 (2024-02-06)

#### Fixes
* Node: Fix set command bug with expiry option ([#1508](https://github.com/aws/glide-for-redis/pull/1508))

## 0.4.0 (2024-05-26)

#### Changes
* Python: Added JSON.DEL JSON.FORGET commands  ([#1146](https://github.com/aws/glide-for-redis/pull/1146))
* Python: Added STRLEN command ([#1230](https://github.com/aws/glide-for-redis/pull/1230))
* Python: Added HKEYS command ([#1228](https://github.com/aws/glide-for-redis/pull/1228))
* Python: Added RPUSHX and LPUSHX commands ([#1239](https://github.com/aws/glide-for-redis/pull/1239))
* Python: Added ZREMRANGEBYSCORE command ([#1151](https://github.com/aws/glide-for-redis/pull/1151))
* Node, Python: Added SPOP, SPOPCOUNT commands. ([#1117](https://github.com/aws/glide-for-redis/pull/1117), [#1261](https://github.com/aws/glide-for-redis/pull/1261))
* Node: Added ZRANGE command ([#1115](https://github.com/aws/glide-for-redis/pull/1115))
* Python: Added RENAME command ([#1252](https://github.com/aws/glide-for-redis/pull/1252))
* Python: Added APPEND command ([#1152](https://github.com/aws/glide-for-redis/pull/1152))
* Python: Added GEOADD command ([#1259](https://github.com/aws/glide-for-redis/pull/1259))
* Python: Added GEODIST command ([#1260](https://github.com/aws/glide-for-redis/pull/1260))
* Python: Added GEOHASH command ([#1281](https://github.com/aws/glide-for-redis/pull/1281))
* Python: Added ZLEXCOUNT command ([#1305](https://github.com/aws/glide-for-redis/pull/1305))
* Python: Added ZREMRANGEBYLEX command ([#1306](https://github.com/aws/glide-for-redis/pull/1306))
* Python: Added LINSERT command ([#1304](https://github.com/aws/glide-for-redis/pull/1304))
* Python: Added GEOPOS command ([#1301](https://github.com/aws/glide-for-redis/pull/1301))
* Node: Added PFADD command ([#1317](https://github.com/aws/glide-for-redis/pull/1317))
* Python: Added PFADD command ([#1315](https://github.com/aws/glide-for-redis/pull/1315))
* Python: Added ZMSCORE command ([#1357](https://github.com/aws/glide-for-redis/pull/1357))
* Python: Added HRANDFIELD command ([#1334](https://github.com/aws/glide-for-redis/pull/1334))
* Node: Added BLPOP command ([#1223](https://github.com/aws/glide-for-redis/pull/1223))
* Python: Added XADD, XTRIM commands ([#1320](https://github.com/aws/glide-for-redis/pull/1320))
* Python: Added BLPOP and BRPOP commands ([#1369](https://github.com/aws/glide-for-redis/pull/1369))
* Python: Added ZRANGESTORE command ([#1377](https://github.com/aws/glide-for-redis/pull/1377))
* Python: Added ZDIFFSTORE command ([#1378](https://github.com/aws/glide-for-redis/pull/1378))
* Python: Added ZDIFF command ([#1401](https://github.com/aws/glide-for-redis/pull/1401))
* Python: Added BZPOPMIN and BZPOPMAX commands ([#1399](https://github.com/aws/glide-for-redis/pull/1399))
* Python: Added ZUNIONSTORE, ZINTERSTORE commands ([#1388](https://github.com/aws/glide-for-redis/pull/1388))
* Python: Added ZRANDMEMBER command ([#1413](https://github.com/aws/glide-for-redis/pull/1413))
* Python: Added BZMPOP command ([#1412](https://github.com/aws/glide-for-redis/pull/1412))
* Python: Added ZINTERCARD command ([#1418](https://github.com/aws/glide-for-redis/pull/1418))
* Python: Added ZMPOP command ([#1417](https://github.com/aws/glide-for-redis/pull/1417))
* Python: Added SMOVE command ([#1421](https://github.com/aws/glide-for-redis/pull/1421))
* Python: Added SUNIONSTORE command ([#1423](https://github.com/aws/glide-for-redis/pull/1423))
* Python: Added SINTER command ([#1434](https://github.com/aws/glide-for-redis/pull/1434))
* Python: Added SDIFF command ([#1437](https://github.com/aws/glide-for-redis/pull/1437))
* Python: Added SDIFFSTORE command ([#1449](https://github.com/aws/glide-for-redis/pull/1449))
* Python: Added SINTERSTORE command ([#1459](https://github.com/aws/glide-for-redis/pull/1459))
* Python: Added SMISMEMBER command ([#1461](https://github.com/aws/glide-for-redis/pull/1461))
* Python: Added SETRANGE command ([#1453](https://github.com/aws/glide-for-redis/pull/1453)

#### Fixes
* Python: Fix typing error "‘type’ object is not subscriptable" ([#1203](https://github.com/aws/glide-for-redis/pull/1203))
* Core: Fixed blocking commands to use the specified timeout from the command argument ([#1283](https://github.com/aws/glide-for-redis/pull/1283))

### Breaking Changes
* Node: Changed `smembers` and `spopCount` functions to return Set instead of string[] ([#1299](https://github.com/aws/glide-for-redis/pull/1299))

#### Features
* Node: Added support for alpine based platform (Or any x64-musl or arm64-musl based platforms) ([#1379](https://github.com/aws/glide-for-redis/pull/1379))

## 0.3.3 (2024-03-28)

#### Fixes

* Node: Fix issue with dual usage, `CommonJS` and `ECMAScript` modules. ([#1199](https://github.com/aws/glide-for-redis/pull/1199))

## 0.3.0 (2024-03-25)

#### Changes

* Python Node: Allow routing Cluster requests by address. ([#1021](https://github.com/aws/glide-for-redis/pull/1021))
* Python, Node: Added HSETNX command. ([#954](https://github.com/aws/glide-for-redis/pull/954), [#1091](https://github.com/aws/glide-for-redis/pull/1091))
* Python, Node: Added SISMEMBER command ([#972](https://github.com/aws/glide-for-redis/pull/972), [#1083](https://github.com/aws/glide-for-redis/pull/1083))
* Python, Node: Added TYPE command ([#945](https://github.com/aws/glide-for-redis/pull/945), [#980](https://github.com/aws/glide-for-redis/pull/980))
* Python, Node: Added HLEN command ([#944](https://github.com/aws/glide-for-redis/pull/944), [#981](https://github.com/aws/glide-for-redis/pull/981))
* Python, Node: Added ZCOUNT command ([#878](https://github.com/aws/glide-for-redis/pull/878)) ([#909](https://github.com/aws/glide-for-redis/pull/909))
* Python, Node: Added ECHO command ([#953](https://github.com/aws/glide-for-redis/pull/953), [#1010](https://github.com/aws/glide-for-redis/pull/1010))
* Python, Node: Added ZPOPMIN command ([#975](https://github.com/aws/glide-for-redis/pull/975), [#1008](https://github.com/aws/glide-for-redis/pull/1008))
* Node: Added STRLEN command ([#993](https://github.com/aws/glide-for-redis/pull/993))
* Node: Added LINDEX command ([#999](https://github.com/aws/glide-for-redis/pull/999))
* Python, Node: Added ZPOPMAX command ([#996](https://github.com/aws/glide-for-redis/pull/996), [#1009](https://github.com/aws/glide-for-redis/pull/1009))
* Python: Added ZRANGE command ([#906](https://github.com/aws/glide-for-redis/pull/906))
* Python, Node: Added PTTL command ([#1036](https://github.com/aws/glide-for-redis/pull/1036), [#1082](https://github.com/aws/glide-for-redis/pull/1082))
* Python, Node: Added HVAL command ([#1130](https://github.com/aws/glide-for-redis/pull/1130)), ([#1022](https://github.com/aws/glide-for-redis/pull/1022))
* Python, Node: Added PERSIST command ([#1129](https://github.com/aws/glide-for-redis/pull/1129)), ([#1023](https://github.com/aws/glide-for-redis/pull/1023))
* Node: Added ZREMRANGEBYSCORE command ([#926](https://github.com/aws/glide-for-redis/pull/926))
* Node: Added ZREMRANGEBYRANK command ([#924](https://github.com/aws/glide-for-redis/pull/924))
* Node: Added Xadd, Xtrim commands. ([#1057](https://github.com/aws/glide-for-redis/pull/1057))
* Python: Added json module and JSON.SET JSON.GET commands  ([#1056](https://github.com/aws/glide-for-redis/pull/1056))
* Python, Node: Added Time command ([#1147](https://github.com/aws/glide-for-redis/pull/1147)), ([#1114](https://github.com/aws/glide-for-redis/pull/1114))
* Python, Node: Added LINDEX command ([#1058](https://github.com/aws/glide-for-redis/pull/1058), [#999](https://github.com/aws/glide-for-redis/pull/999))
* Python, Node: Added ZRANK command ([#1065](https://github.com/aws/glide-for-redis/pull/1065), [#1149](https://github.com/aws/glide-for-redis/pull/1149))
* Core: Enabled Cluster Mode periodic checks by default ([#1089](https://github.com/aws/glide-for-redis/pull/1089))
* Node: Added Rename command. ([#1124](https://github.com/aws/glide-for-redis/pull/1124))
* Python: Added JSON.TOGGLE command ([#1184](https://github.com/aws/glide-for-redis/pull/1184))

#### Features

* Python: Allow chaining function calls on transaction. ([#987](https://github.com/aws/glide-for-redis/pull/987))
* Node: Adding support for GLIDE's usage in projects based on either `CommonJS` or `ECMAScript` modules. ([#1132](https://github.com/aws/glide-for-redis/pull/1132))
* Python, Node: Added Cluster Mode configuration for periodic checks interval ([#1089](https://github.com/aws/glide-for-redis/pull/1089), [#1158](https://github.com/aws/glide-for-redis/pull/1158))

## 0.2.0 (2024-02-11)

#### Changes
* Python, Node: Added ZCARD command ([#871](https://github.com/aws/glide-for-redis/pull/871), [#885](https://github.com/aws/glide-for-redis/pull/885))
* Python, Node: Added ZADD and ZADDINCR commands ([#814](https://github.com/aws/glide-for-redis/pull/814), [#830](https://github.com/aws/glide-for-redis/pull/830))
* Python, Node: Added ZREM command ([#834](https://github.com/aws/glide-for-redis/pull/834), [#831](https://github.com/aws/glide-for-redis/pull/831))
* Python, Node: Added ZSCORE command ([#877](https://github.com/aws/glide-for-redis/pull/877), [#889](https://github.com/aws/glide-for-redis/pull/889))
* Use jemalloc as default allocator. ([#847](https://github.com/aws/glide-for-redis/pull/847))
* Python, Node: Added RPOPCOUNT and LPOPCOUNT to transaction ([#874](https://github.com/aws/glide-for-redis/pull/874))
* Standalone client: Improve connection errors. ([#854](https://github.com/aws/glide-for-redis/pull/854))
* Python, Node: When recieving LPOP/RPOP with count, convert result to Array. ([#811](https://github.com/aws/glide-for-redis/pull/811))
* Python, Node: Added TYPE command ([#945](https://github.com/aws/glide-for-redis/pull/945), [#980](https://github.com/aws/glide-for-redis/pull/980))
* Python, Node: Added HLEN command ([#944](https://github.com/aws/glide-for-redis/pull/944), [#981](https://github.com/aws/glide-for-redis/pull/981))
* Python, Node: Added ZCOUNT command ([#878](https://github.com/aws/glide-for-redis/pull/878)) ([#909](https://github.com/aws/glide-for-redis/pull/909))
* Python: Added ECHO command ([#953](https://github.com/aws/glide-for-redis/pull/953))
* Python, Node: Added ZPOPMIN command ([#975](https://github.com/aws/glide-for-redis/pull/975), [#1008](https://github.com/aws/glide-for-redis/pull/1008))
* Node: Added STRLEN command ([#993](https://github.com/aws/glide-for-redis/pull/993))
* Node: Added LINDEX command ([#999](https://github.com/aws/glide-for-redis/pull/999))
* Python, Node: Added ZPOPMAX command ([#996](https://github.com/aws/glide-for-redis/pull/996), [#1009](https://github.com/aws/glide-for-redis/pull/1009))
* Python: Added DBSIZE command ([#1040](https://github.com/aws/glide-for-redis/pull/1040))

#### Features
* Python, Node: Added support in Lua Scripts ([#775](https://github.com/aws/glide-for-redis/pull/775), [#860](https://github.com/aws/glide-for-redis/pull/860))
* Node: Allow chaining function calls on transaction. ([#902](https://github.com/aws/glide-for-redis/pull/902))

#### Fixes
* Core: Fixed `Connection Refused` error not to close the client ([#872](https://github.com/aws/glide-for-redis/pull/872))
* Socket listener: fix identifier for closed reader error. ([#853](https://github.com/aws/glide-for-redis/pull/853))
* Node: Fix issues with type import & exports ([#767](https://github.com/aws/glide-for-redis/pull/767))
* Core: Added handling to "?" and NULL hostnames in CLUSTER SLOTS ([#104](https://github.com/amazon-contributing/redis-rs/pull/104))
* Core: Cluster connection now reconnects after full disconnect. ([#100](https://github.com/amazon-contributing/redis-rs/pull/100))

## 0.1.0 (2024-01-17)

Preview release of **GLIDE for Redis** a Polyglot Redis client.

See the [README](README.md) for additional information.
