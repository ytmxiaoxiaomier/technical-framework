package com.example.mq.spring.processor;

import org.springframework.context.expression.StandardBeanExpressionResolver;

public interface SpelExpression {

    /**
     * 解析表达式
     */
    default String resolveExpression(String expression) {
        if (expression == null || !expression.startsWith("${") || !expression.endsWith("}")) {
            return expression;
        }

        Object evaluationResult = expressionResolver().evaluate(expression, null);
        // 将结果转换为String
        String value = evaluationResult != null ? evaluationResult.toString() : null;
        return value != null ? value : expression;
    }

    StandardBeanExpressionResolver expressionResolver();

}
