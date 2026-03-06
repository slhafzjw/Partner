package work.slhaf.partner.api.agent.factory.config.pojo

import lombok.Data

@Data
data class ModelConfig(
    val baseUrl: String,
    val apikey: String,
    val model: String
)
