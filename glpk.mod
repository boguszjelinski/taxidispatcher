# glpksol -m glpk.mod

set I;
set J;

param filename, symbolic := "out.txt";
param c{i in I, j in J};

var x{i in I, j in J} >= 0;
minimize cost: sum{i in I, j in J} c[i,j] * x[i,j];

s.t. supply{i in I}: sum{j in J} x[i,j] = 1;
s.t. demand{j in J}: sum{i in I} x[i,j] = 1;

solve;

for {j in J} {
    for {i in I} {
        printf "%d\n", x[i,j] >> filename;
    }
}

data;
set I := 1 2 3 4;
set J := 1 2 3 4;

param c :     1 2 3 4 :=
           1  5 1 9 100
           2  5 1 9 100 
           3  0 3 5 100
           4  5 8 0 100;
end;
