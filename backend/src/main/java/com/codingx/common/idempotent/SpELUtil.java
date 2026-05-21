package com.codingx.common.idempotent;

import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.util.ArrayUtil;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

/**
 * SpEL 表达式解析工具。
 */
public final class SpELUtil {

    private static final DefaultParameterNameDiscoverer PARAMETER_NAME_DISCOVERER = new DefaultParameterNameDiscoverer();
    private static final ExpressionParser EXPRESSION_PARSER = new SpelExpressionParser();

    private SpELUtil() {
    }

    /**
     * 校验并返回实际使用的 SpEL 表达式值。
     * @param spEl SpEL 表达式或纯字符串。
     * @param method 目标方法。
     * @param contextObj 调用参数。
     * @return 解析后的键值对象。
     */
    public static Object parseKey(String spEl, Method method, Object[] contextObj) {
        List<String> spELFlag = ListUtil.of("#", "T(");
        Optional<String> optional = spELFlag.stream().filter(spEl::contains).findFirst();
        if (optional.isPresent()) {
            return parse(spEl, method, contextObj);
        }
        return spEl;
    }

    /**
     * 执行 SpEL 解析。
     * @param spEl SpEL 表达式。
     * @param method 目标方法。
     * @param contextObj 调用参数。
     * @return 解析结果。
     */
    public static Object parse(String spEl, Method method, Object[] contextObj) {
        Expression expression = EXPRESSION_PARSER.parseExpression(spEl);
        String[] params = PARAMETER_NAME_DISCOVERER.getParameterNames(method);
        StandardEvaluationContext context = new StandardEvaluationContext();
        if (ArrayUtil.isNotEmpty(params)) {
            for (int index = 0; index < params.length; index++) {
                context.setVariable(params[index], contextObj[index]);
            }
        }
        return expression.getValue(context);
    }
}
