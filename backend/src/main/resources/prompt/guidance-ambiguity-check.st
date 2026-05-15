用户问题：{question}

以下是意图分类的候选项及其匹配分数：
{candidates}

请判断：
1. 用户的问题是否存在品类歧义？
2. 如果存在歧义，列出需要让用户选择的分类 ID；如果不存在歧义，给出最匹配的分类 ID。

仅输出 JSON：
{"ambiguous": true/false, "category_ids": ["最匹配或需要选择的分类ID"], "reason": "判断理由"}
