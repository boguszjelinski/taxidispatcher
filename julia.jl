using JuMP, GLPK
model =  Model(with_optimizer(GLPK.Optimizer))
n=3
m=4
t = [5 5 0 5; 1 1 3 8; 9 9 5 0]
@variable(model, x[1:n,1:m], Bin);
@objective(model, Min, sum(t[i,j]*x[i,j] for i in 1:n, j in 1:m));
@constraint(model, [i=1:n], sum(x[i,j] for j in 1:m) == 1);
@constraint(model, [j=1:m], sum(x[i,j] for i in 1:n) <= 1);
optimize!(model)
for i= 1:n
    for j= 1:m
        println(value(x[i,j]))
    end
end