package work.slhaf.partner.core.action.entity


/**
 * 行动链中的单一元素，封装了调用外部行动程序的必要信息与结果容器，可被[work.slhaf.partner.core.action.ActionCapability]执行
 */
data class MetaAction(
    /**
     * 行动name，用于标识行动程序
     */
    val name: String,
    /**
     * 是否IO密集，用于决定使用何种线程池
     */
    val io: Boolean = false,
    /**
     * 行动程序类型，可分为 MCP、ORIGIN 两种，前者对应读取到的 MCP Tool、后者对应生成的临时行动程序
     */
    val type: Type,
    /**
     * 当类型为 MCP 时，该字段对应相应 MCP Client 注册时生成的 id;
     * 当类型为 ORIGIN 时，该字段对应相应的磁盘路径字符串
     */
    val location: String,
) {

    /**
     * 行动程序可接受的参数，由调用处设置
     */
    val params: MutableMap<String, Any> = mutableMapOf()

    /**
     * 行动结果，包括执行状态和相应内容(执行结果或者错误信息)
     */
    var result = Result()

    val key: String
        /**
         * actionKey 将由 location+name 共同定位
         * 
         * @return actionKey
         */
        get() = "$location::$name"

    class Result {
        var status = Status.WAITING
        var data: String? = null

        fun reset() {
            status = Status.WAITING
            data = null
        }

        enum class Status {
            SUCCESS,
            FAILED,
            WAITING
        }
    }

    enum class Type {
        /**
         * 将调用的 MCP 工具，可包括远程、本地任意服务
         */
        MCP,

        /**
         * 适用于‘临时生成’的行动程序，在生成后根据序列化选项及执行情况，进行持久化
         */
        ORIGIN
    }

}
