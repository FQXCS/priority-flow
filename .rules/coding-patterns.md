# 数据模型规范

## VO 规范

- **VO 继承 PO（Entity）模式**：列表/详情 VO 应继承对应的 PO 实体，自动拥有 PO 全部字段。
  - 示例：`RequirementInfoVo extends RequirementInfo`
  - 示例：`ConcentrateOperationDetailVo extends ConcentrateOperationApplyDo`
- VO 中仅添加 PO 不具备的额外字段（翻译字段 `XXXStr`、文件列表、关联对象等）。

## Entity 规范
          
- **主表 Entity 必须继承 `BaseEntity`**，自动获得公共字段（id、create_by、create_time、update_by、update_time、remark、del_flag）。
- **子表可以不继承 `BaseEntity`**，根据业务需要自行定义字段。

```java
@Data
@TableName("表名")
@ApiModel(value = "描述")
public class EntityName extends BaseEntity {
    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "字段描述")
    @Length(max = 255)
    private String fieldName;

    @TableField(exist = false)
    @ApiModelProperty(value = "非数据库字段")
    private List<ChildEntity> children;
}
```

## 建表规范

- 主表必须包含 `BaseEntity` 的公共字段。
- **列顺序**：`id` 放在第一列，公共审计字段放在表的最后，顺序依次为 `create_by` → `create_time` → `update_by` → `update_time` → `remark` → `del_flag`。

| 字段 | 类型 | 说明 |
|------|------|----|
| `id` | `varchar(128)` | UUID 主键（`IdType.ASSIGN_UUID`） |
| `create_by` | `varchar(128)` | 创建者（自动填充） |
| `create_time` | `timestamp(6)` | 创建时间（自动填充） |
| `update_by` | `varchar(128)` | 更新者（自动填充） |
| `update_time` | `timestamp(6)` | 更新时间（自动填充） |
| `remark` | `text` | 备注 |
| `del_flag` | `varchar(2) DEFAULT '0'` | 删除标记（0 正常 1 删除，`@TableLogic`） |

- 表名使用下划线命名，与 Entity 类名对应（如 `project_info` 对应 `ProjectInfo`）。
- 非数据库字段使用 `@TableField(exist = false)` 标注。
- PO（Entity）的非数据库字段使用 `@TableField(exist = false)` 标注。

## Controller 规范

```java
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/path")
@Api(value = "ControllerName", tags = "接口描述")
public class XxxController {
    private final XxxService xxxService;

    @ApiOperation(value = "接口描述")
    @GetMapping("/endpoint")
    @PreAuthorize("@pms.hasPermi('permission:code')")
    public R<ResultType> methodName(ParamDto param) {
        return R.ok(service.method(param));
    }
}
```
