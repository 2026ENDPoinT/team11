class APIError(Exception):
    def __init__(self, code, message, status=400, field=None, details=None):
        super().__init__(message)
        self.code, self.status, self.field = code, status, field
        self.details = details

    def body(self):
        error = {"code": self.code, "message": str(self)}
        if self.field:
            error["details"] = [{"field": self.field, "reason": str(self)}]
        elif self.details is not None:
            error["details"] = self.details
        return {"error": error}
