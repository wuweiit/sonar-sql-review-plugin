package com.yourcompany.sqlreview;

import com.yourcompany.sqlreview.rules.SqlRulesDefinition;
import com.yourcompany.sqlreview.sensor.MyBatisXmlSensor;
import com.yourcompany.sqlreview.settings.SqlReviewProperties;
import org.sonar.api.Plugin;

/**
 * SonarQube SQL Review 插件入口
 * 注册 Sensor、规则定义和全局配置项
 *
 * @author marker
 */
public class SqlReviewPlugin implements Plugin {

    @Override
    public void define(Context context) {
        // 注册全局配置项
        context.addExtensions(SqlReviewProperties.getProperties());

        // 注册规则定义
        context.addExtension(SqlRulesDefinition.class);

        // 注册 Sensor
        context.addExtension(MyBatisXmlSensor.class);
    }
}
