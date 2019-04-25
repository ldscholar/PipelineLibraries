//var/nodeParallel.groovy

/**
 * 入口函数
 * @param nodes 节点数组
 * @param func 需要再每个节点上调用的方法
 * @return
 */
def call(nodes, func) {
    def syncTasks = [:]
    for (x in nodes) {
        //这里必须关闭标签变量绑定 - 不能这样写'for (server in allNode)'
        def server = x
        syncTasks[server] = {
            node(server) {
                func.call()
            }
        }
    }
    parallel syncTasks
}

