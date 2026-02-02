---
paths:
  - "backend/src/test/**/*ControllerTest.java"
---

# Controller Testing Patterns

## Template

```java
@WebMvcTest(UserController.class)
class UserControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @Test
    void getUser_WhenExists_Returns200() throws Exception {
        when(userService.findById(1L))
            .thenReturn(new UserResponse(1L, "Test", "test@example.com"));

        mockMvc.perform(get("/api/users/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Test"));
    }

    @Test
    void createUser_WithInvalidData_Returns400() throws Exception {
        // Jakarta Validation triggers on @Valid @RequestBody
        mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"\", \"email\": \"invalid\"}"))
            .andExpect(status().isBadRequest());
    }
}
```

**Reference:** `PlannerControllerTest.java`
