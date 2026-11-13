"""Shared API field-name conversion, independent of calculation models."""


def camel(value):
    if isinstance(value, dict):
        def key(name):
            first, *rest = name.split("_")
            return first + "".join(part.capitalize() for part in rest)
        return {key(k): camel(v) for k, v in value.items()}
    if isinstance(value, (tuple, list)):
        return [camel(item) for item in value]
    return value
