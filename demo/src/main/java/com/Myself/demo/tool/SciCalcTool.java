package com.Myself.demo.tool;

import com.Myself.demo.service.SciCalcService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SciCalcTool {

    private final SciCalcService sciCalcService;

    public SciCalcTool(SciCalcService sciCalcService) {
        this.sciCalcService = sciCalcService;
    }

    @Tool(name = "sci_calc", value = "科学计算器，支持三角函数(sin/cos/tan/cot)、反三角函数(asin/acos/atan)、双曲函数(sinh/cosh/tanh)、对数(log/ln)、阶乘(factorial)、平方根(sqrt)、幂运算(pow)、绝对值(abs)、取整(ceil/round)、百分比(percentage)、角度弧度转换(deg2rad/rad2deg)等。单参数运算num传一个数字，双参数运算num用逗号分隔两个数字，如'2,10'。")
    public String calculate(
            @P("运算类型，可选值: sqrt(平方根), pow(幂运算,需两个数如2,10), sin(正弦), cos(余弦), tan(正切), cot(余切), asin(反正弦), acos(反余弦), atan(反正切), sinh(双曲正弦), cosh(双曲余弦), tanh(双曲正切), log(自然对数ln), exp(自然指数e^x), factorial(阶乘), abs(绝对值), ceil(向上取整), round(四舍五入,第二个参数为小数位数), percentage(百分比,第一个为分子第二个为分母), mod(取余), deg2rad(角度转弧度), rad2deg(弧度转角度), fmod(浮点数取余), hypot(斜边长), acosh(反双曲余弦), asinh(反双曲正弦), atanh(反双曲正切)") String type,
            @P("要计算的数值。单参数运算如'144'，双参数运算如'2,10'（两个数用英文逗号分隔）。例如平方根sqrt传'144'，幂运算pow传'2,10'，百分比传'30,150'表示30占150的百分比，四舍五入round传'3.14159,2'保留两位小数。") String num) {
        log.info("科学计算: type={}, num={}", type, num);
        return sciCalcService.calculate(type, num);
    }
}
