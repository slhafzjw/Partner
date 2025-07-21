package work.slhaf.partner.module.common.module;


/**
 * 子模块基类
 * @param <I> 输入类型
 * @param <O> 输出类型
 */
public abstract class SubModule<I, O> extends Module {

    public abstract O execute(I data);

}
